// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lsu

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._
import chisel3.probe.{Probe, ProbeValue, define}
import org.chipsalliance.t1.rtl.{VRFWriteRequest, cutUInt, multiShifter}

@instantiable
class LoadUnit(param: MSHRParam) extends StrideBase(param) with LSUPublic {
  /** TileLink Port which will be route to the [[LSU.tlPort]]. */
  @public
  val memRequest: DecoupledIO[MemRequest] = IO(Decoupled(new MemRequest(param)))
  @public
  val memResponse: DecoupledIO[MemDataBundle] = IO(Flipped(Decoupled(new MemDataBundle(param))))
  @public
  val status: LSUBaseStatus = IO(Output(new LSUBaseStatus))
  @public
  val writeReadyForLsu: Bool = IO(Input(Bool()))

  /** write channel to [[V]], which will redirect it to [[Lane.vrf]].
   * see [[LSU.vrfWritePort]]
   */
  @public
  val vrfWritePort: Vec[DecoupledIO[VRFWriteRequest]] = IO(Vec(param.laneNumber,
    Decoupled(
      new VRFWriteRequest(param.regNumBits, param.vrfOffsetBits, param.instructionIndexBits, param.datapathWidth)
    )
  ))

  val nextCacheLineIndex = Wire(UInt(param.cacheLineIndexBits.W))
  val cacheLineIndex = RegEnable(Mux(lsuRequest.valid, 0.U, nextCacheLineIndex), memRequest.fire || lsuRequest.valid)
  nextCacheLineIndex := cacheLineIndex + 1.U

  val validInstruction = !invalidInstruction && lsuRequest.valid
  val lastRequest: Bool = cacheLineNumberReg === cacheLineIndex
  val sendRequest: Bool =
    RegEnable(lsuRequest.valid && (csrInterface.vl > 0.U), false.B, validInstruction || (memRequest.fire && lastRequest))

  val requestAddress = ((lsuRequestReg.rs1Data >> param.cacheLineBits).asUInt + cacheLineIndex) ##
    0.U(param.cacheLineBits.W)
  val writeReadyReg: Bool =
    RegEnable(writeReadyForLsu && !lsuRequest.valid, false.B, lsuRequest.valid || writeReadyForLsu)

  memRequest.bits.src := cacheLineIndex
  memRequest.bits.address := requestAddress
  memRequest.valid := sendRequest && !addressConflict

  val anyLastCacheLineAck: Bool = memResponse.fire && (memResponse.bits.index === cacheLineNumberReg)

  // 接收拼凑出来的cache line
  // 对齐
  val alignedDequeue: DecoupledIO[MemDataBundle] = Wire(Decoupled(new MemDataBundle(param)))
  val unalignedCacheLine: ValidIO[MemDataBundle] = RegInit(0.U.asTypeOf(Valid(new MemDataBundle(param))))
  val unalignedEnqueueReady: Bool = alignedDequeue.ready || !unalignedCacheLine.valid

  memResponse.ready := unalignedEnqueueReady
  val nextIndex: UInt = Mux(unalignedCacheLine.valid, unalignedCacheLine.bits.index + 1.U, 0.U)
  val dataValid: Bool = memResponse.valid
  val unalignedEnqueueFire: Bool = memResponse.fire

  val alignedDequeueValid: Bool =
    unalignedCacheLine.valid &&
      (dataValid || (unalignedCacheLine.bits.index === cacheLineNumberReg && lastCacheNeedPush))
  // update unalignedCacheLine
  when(unalignedEnqueueFire) {
    unalignedCacheLine.bits.data := memResponse.bits.data
    unalignedCacheLine.bits.index := nextIndex
  }

  when((unalignedEnqueueFire ^ alignedDequeue.fire) || lsuRequest.valid) {
    unalignedCacheLine.valid := unalignedEnqueueFire
  }

  alignedDequeue.valid := alignedDequeueValid
  alignedDequeue.bits.data :=
    multiShifter(right = true, multiSize = 8)(memResponse.bits.data ## unalignedCacheLine.bits.data, initOffset)
  alignedDequeue.bits.index := unalignedCacheLine.bits.index

  val bufferFull: Bool = RegInit(false.B)
  val bufferTailFire: Bool = Wire(Bool())
  val bufferDequeueValid: Bool = bufferFull || bufferTailFire
  val bufferDequeueReady: Bool = Wire(Bool())
  val bufferDequeueFire: Bool = bufferDequeueReady && bufferDequeueValid

  alignedDequeue.ready := !bufferFull
  val bufferEnqueueSelect: UInt = Mux(
    alignedDequeue.fire,
    UIntToOH(cacheLineIndexInBuffer),
    0.U
  )

  val dataBufferUpdate: Vec[UInt] = VecInit(dataBuffer.zipWithIndex.map {case (d, i) =>
    when(bufferEnqueueSelect(i)) {d := alignedDequeue.bits.data}
    Mux(bufferEnqueueSelect(i), alignedDequeue.bits.data, d)
  })
  val dataSelect: Vec[UInt] = Mux(bufferFull, dataBuffer, dataBufferUpdate)
  val lastCacheLineForThisGroup: Bool = cacheLineIndexInBuffer === lsuRequestReg.instructionInformation.nf
  val lastCacheLineForInst: Bool = alignedDequeue.bits.index === lastWriteVrfIndexReg
  bufferTailFire := alignedDequeue.fire && (lastCacheLineForThisGroup || lastCacheLineForInst)
  // update cacheLineIndexInBuffer
  when(alignedDequeue.fire || bufferDequeueFire) {
    cacheLineIndexInBuffer := Mux(bufferDequeueFire, 0.U, cacheLineIndexInBuffer + 1.U)
  }

  when(bufferTailFire || bufferDequeueFire) {
    bufferFull := !bufferDequeueFire
  }

  when(alignedDequeue.fire && cacheLineIndexInBuffer === 0.U) {
    bufferBaseCacheLineIndex := alignedDequeue.bits.index
  }

  val waitForFirstDataGroup: Bool = RegEnable(lsuRequest.fire, false.B, lsuRequest.fire || bufferDequeueFire)
  when(bufferDequeueFire) {
    dataGroup := Mux(waitForFirstDataGroup, 0.U, dataGroup + 1.U)
  }
  val lastPtr: Bool = accessPtr === 0.U
  val writeStageReady: Bool = lastPtr && accessStateCheck

  bufferDequeueReady := writeStageReady

  when(bufferDequeueFire || lsuRequest.valid) {
    maskCounterInGroup := Mux(isLastDataGroup || lsuRequest.valid, 0.U, nextMaskCount)
  }
  when(lsuRequest.valid) {
    maskGroupCounter := 0.U
  }
  // 是否可以反向写vrf, 然后第一组24选1的multi cycle
  when(bufferDequeueFire) {
    // 总是换mask组
    when(isLastDataGroup) {
      maskSelect.valid := true.B
      maskGroupCounter := nextMaskGroup
    }
    accessData := Mux1H(dataEEWOH, Seq.tabulate(3) { sewSize =>
      Mux1H(UIntToOH(lsuRequestReg.instructionInformation.nf), Seq.tabulate(8) { segSize =>
        // 32 byte
        // 每个数据块 2 ** sew byte
        val dataBlockSize = 1 << sewSize
        // 总共需要32byte数据, 会有 32/dataBlockSize 个数据块
        val blockSize = (param.datapathWidth * param.laneNumber / 8) / dataBlockSize
        val nFiled = segSize + 1
        // 一次element会用掉多少 byte 数据
        val elementSize = dataBlockSize * nFiled
        VecInit(Seq.tabulate(8) { segIndex =>
          val res = Wire(UInt((param.lsuTransposeSize * 8).W))
          if (segIndex > segSize) {
            // todo: 优化这个 DontCare
            res := DontCare
          } else {
            val dataGroup: Seq[UInt] = Seq.tabulate(blockSize) { elementIndex =>
              val basePtr = elementSize * elementIndex + dataBlockSize * segIndex
              dataSelect.asUInt((basePtr + dataBlockSize) * 8 - 1, basePtr * 8)
            }
            res := VecInit(dataGroup).asUInt
          }
          res
        }).asUInt.suggestName(s"regroupLoadData_${sewSize}_$segSize")
      })
    }).asTypeOf(accessData)
  }

  when(bufferDequeueFire) {
    maskForGroup := maskForGroupWire
  }

  // 往vrf写数据
  Seq.tabulate(param.laneNumber) { laneIndex =>
    val writePort: DecoupledIO[VRFWriteRequest] = vrfWritePort(laneIndex)
    writePort.valid := accessState(laneIndex) && writeReadyReg
    writePort.bits.mask := cutUInt(maskForGroup, param.datapathWidth / 8)(laneIndex)
    writePort.bits.data := cutUInt(Mux1H(UIntToOH(accessPtr), accessData), param.datapathWidth)(laneIndex)
    writePort.bits.offset := dataGroup
    writePort.bits.vd :=
      lsuRequestReg.instructionInformation.vs3 + accessPtr * segmentInstructionIndexInterval + (dataGroup >> writePort.bits.offset.getWidth).asUInt
    writePort.bits.last := DontCare
    writePort.bits.instructionIndex := lsuRequestReg.instructionIndex
    when(writePort.fire) {
      accessState(laneIndex) := false.B
      accessStateUpdate(laneIndex) := false.B
    }
  }
  val sendStateReg = RegEnable(initSendState, 0.U.asTypeOf(initSendState), bufferDequeueFire)
  when(bufferDequeueFire || (accessStateCheck && !lastPtr)) {
    accessState := Mux(bufferDequeueFire, initSendState, sendStateReg)
    accessPtr := Mux(bufferDequeueFire, lsuRequestReg.instructionInformation.nf, accessPtr - 1.U)
  }

  val lastCacheRequest: Bool = lastRequest && memRequest.fire
  val lastCacheRequestReg: Bool = RegEnable(lastCacheRequest, true.B, lastCacheRequest || validInstruction)
  val lastCacheLineAckReg: Bool = RegEnable(anyLastCacheLineAck, true.B, anyLastCacheLineAck || validInstruction)
  val bufferClear: Bool =
    !(
      memResponse.valid ||
        // 对齐的空了
        alignedDequeue.valid ||
        bufferFull ||
        // 发送单元结束
        !writeStageReady
      )
  status.idle := lastCacheRequestReg && lastCacheLineAckReg && bufferClear && !sendRequest
  val idleNext: Bool = RegNext(status.idle, true.B)
  status.last := (!idleNext && status.idle) || invalidInstructionNext
  status.changeMaskGroup := maskSelect.valid && !lsuRequest.valid
  status.instructionIndex := lsuRequestReg.instructionIndex
  status.startAddress := requestAddress
  status.endAddress := ((lsuRequestReg.rs1Data >> param.cacheLineBits).asUInt + cacheLineNumberReg) ##
    0.U(param.cacheLineBits.W)
  dontTouch(status)

  /**
    * Internal signals probes
    */
  // Load Unit ready to accpet LSU request
  @public
  val lsuRequestValidProbe = IO(Output(Probe(Bool(), layers.Verification)))

  // Load Unit is idle
  @public
  val idleProbe = IO(Output(Probe(Bool(), layers.Verification)))

  // Tilelink Channel A decouple IO status
  // ready: channel A is ready to accept signal
  // valid: Load Unit try to send signal to channel A
  @public
  val tlPortAValidProbe = IO(Output(Probe(Bool(), layers.Verification)))
  @public
  val tlPortAReadyProbe = IO(Output(Probe(Bool(), layers.Verification)))

  // Fail to send signal to tilelink Channel A because of address conflict
  @public
  val addressConflictProbe = IO(Output(Probe(Bool(), layers.Verification)))

  //  // Tilelink used for accepting signal from receive signal from Channel D
  //  @public
  //  val tlPortDValidProbe: Seq[Bool] = tlPortD.map(port => {
  //    val probe = IO(Output(Probe(Bool())))
  //    define(probe, ProbeValue(port.valid))
  //    probe
  //  }).toSeq
  //  @public
  //  val tlPortDReadyProbe: Seq[Bool] = tlPortD.map(port => {
  //    val probe = IO(Output(Probe(Bool())))
  //    define(probe, ProbeValue(port.ready))
  //    probe
  //  }).toSeq
  //
  //  // Store data from tilelink Channel D, each item corresponding to tlPortD port index
  //  @public
  //  val queueValidProbe = queue.map(io => {
  //    val probe = IO(Output(Probe(Bool())))
  //    define(probe, ProbeValue(io.valid))
  //    probe
  //  })
  //  @public
  //  val queueReadyProbe = queue.map(io => {
  //    val probe = IO(Output(Probe(Bool())))
  //    define(probe, ProbeValue(io.ready))
  //    probe
  //  })
  //
  //  // After reading data from tilelink channel D, data is concat into a full form cacheline, then go to lower level through cachelineDequeue
  //  @public
  //  val cacheLineDequeueValidProbe: Seq[Bool] = cacheLineDequeue.map(port => {
  //    val probe = IO(Output(Probe(Bool())))
  //    define(probe, ProbeValue(port.valid))
  //    probe
  //  }).toSeq
  //  @public
  //  val cacheLineDequeueReadyProbe: Seq[Bool] = cacheLineDequeue.map(port => {
  //    val probe = IO(Output(Probe(Bool())))
  //    define(probe, ProbeValue(port.ready))
  //    probe
  //  }).toSeq

  // After receiving new cacheline from top, or current item is the last cacheline,
  // pop out data and transform it to an aligned cacheline, go through alignedDequeue to next level
  @public
  val unalignedCacheLineProbe = IO(Output(Probe(Bool(), layers.Verification)))

  // Used for transmitting data from unalignedCacheline to dataBuffer
  @public
  val alignedDequeueValidProbe = IO(Output(Probe(Bool(), layers.Verification)))

  @public
  val alignedDequeueReadyProbe = IO(Output(Probe(Bool(), layers.Verification)))

  @public
  val bufferEnqueueSelectProbe = IO(Output(Probe(chiselTypeOf(bufferEnqueueSelect), layers.Verification)))

  // Load Unit can write VRF after writeReadyForLSU is true
  @public
  val writeReadyForLSUProbe: Bool = IO(Output(Probe(chiselTypeOf(writeReadyForLsu), layers.Verification)))


  // Write to VRF
  @public
  val vrfWriteValidProbe: Seq[Bool] = vrfWritePort.map(port => {
    val probe = IO(Output(Probe(Bool(), layers.Verification)))
    layer.block(layers.Verification) {
      define(probe, ProbeValue(port.valid))
    }
    probe
  })
  @public
  val vrfWriteReadyProbe: Seq[Bool] = vrfWritePort.map(port => {
    val probe = IO(Output(Probe(Bool(), layers.Verification)))
    layer.block(layers.Verification) {
      define(probe, ProbeValue(port.ready))
    }
    probe
  }).toSeq

  layer.block(layers.Verification) {
    define(lsuRequestValidProbe, ProbeValue(lsuRequest.valid))
    define(idleProbe, ProbeValue(status.idle))
    define(tlPortAValidProbe, ProbeValue(memRequest.valid))
    define(tlPortAReadyProbe, ProbeValue(memRequest.ready))
    define(addressConflictProbe, ProbeValue(addressConflict))
    define(unalignedCacheLineProbe, ProbeValue(unalignedCacheLine.valid))
    define(alignedDequeueValidProbe, ProbeValue(alignedDequeue.valid))
    define(alignedDequeueReadyProbe, ProbeValue(alignedDequeue.ready))
    define(bufferEnqueueSelectProbe, ProbeValue(bufferEnqueueSelect))
    define(writeReadyForLSUProbe, ProbeValue(writeReadyForLsu))
  }

}
