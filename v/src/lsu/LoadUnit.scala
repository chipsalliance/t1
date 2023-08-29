// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package v
import chisel3._
import chisel3.util._
import lsu.LSUBaseStatus
import tilelink.{TLChannelA, TLChannelD}

class cacheLineDequeueBundle(param: MSHRParam) extends Bundle {
  val data: UInt = UInt((param.cacheLineSize * 8).W)
  val index: UInt = UInt(param.cacheLineIndexBits.W)
}

class LoadUnit(param: MSHRParam) extends StrideBase(param)  with LSUPublic {
  /** TileLink Port which will be route to the [[LSU.tlPort]]. */
  val tlPortA: DecoupledIO[TLChannelA] = IO(param.tlParam.bundle().a)
  val tlPortD: Vec[DecoupledIO[TLChannelD]] = IO(Vec(param.memoryBankSize, param.tlParam.bundle().d))
  val status: LSUBaseStatus = IO(Output(new LSUBaseStatus))
  val writeReadyForLsu: Bool = IO(Input(Bool()))

  /** write channel to [[V]], which will redirect it to [[Lane.vrf]].
   * see [[LSU.vrfWritePort]]
   */
  val vrfWritePort: Vec[DecoupledIO[VRFWriteRequest]] = IO(Vec(param.laneNumber,
    Decoupled(
      new VRFWriteRequest(param.regNumBits, param.vrfOffsetBits, param.instructionIndexBits, param.datapathWidth)
    )
  ))

  val nextCacheLineIndex = Wire(UInt(param.cacheLineIndexBits.W))
  val cacheLineIndex = RegEnable(Mux(lsuRequest.valid, 0.U, nextCacheLineIndex), tlPortA.fire || lsuRequest.valid)
  nextCacheLineIndex := cacheLineIndex + 1.U

  /** How many byte will be accessed by this instruction */
  val bytePerInstruction = ((nFiled * csrInterface.vl) << lsuRequest.bits.instructionInformation.eew).asUInt

  val baseAddressAligned: Bool = !lsuRequest.bits.rs1Data(param.cacheLineBits - 1, 0).orR
  val baseAddressAlignedReg: Bool = RegEnable(baseAddressAligned, false.B, lsuRequest.valid)

  /** How many cache lines will be accessed by this instruction
   * nFiled * vl * (2 ** eew) / 32
   */
  val lastCacheLineIndex: UInt = (bytePerInstruction >> param.cacheLineBits).asUInt +
    bytePerInstruction(param.cacheLineBits - 1, 0).orR - baseAddressAligned

  val cacheLineNumberReg: UInt = RegEnable(lastCacheLineIndex, 0.U, lsuRequest.valid)
  val validInstruction = !invalidInstruction && lsuRequest.valid
  val lastRequest: Bool = cacheLineNumberReg === cacheLineIndex
  val sendRequest: Bool =
    RegEnable(lsuRequest.valid && (csrInterface.vl > 0.U), false.B, validInstruction || (tlPortA.fire && lastRequest))

  val requestAddress = ((lsuRequestReg.rs1Data >> param.cacheLineBits).asUInt + cacheLineIndex) ##
    0.U(param.cacheLineBits.W)
  val writeReadyReg: Bool =
    RegEnable(writeReadyForLsu && !lsuRequest.valid, false.B, lsuRequest.valid || writeReadyForLsu)

  tlPortA.bits.opcode := 4.U
  tlPortA.bits.param := 0.U
  tlPortA.bits.size := param.cacheLineBits.U
  tlPortA.bits.source := cacheLineIndex
  tlPortA.bits.address := requestAddress
  tlPortA.bits.mask := -1.S(tlPortA.bits.mask.getWidth.W).asUInt
  tlPortA.bits.data := 0.U
  tlPortA.bits.corrupt := false.B
  tlPortA.valid := sendRequest

  val queue: Seq[DecoupledIO[TLChannelD]] =
    Seq.tabulate(param.memoryBankSize)(index => Queue(tlPortD(index), burstSize))

  val lastCacheLineAck: Vec[Bool] = Wire(Vec(param.memoryBankSize, Bool()))
  val anyLastCacheLineAck = lastCacheLineAck.asUInt.orR
  val cacheLineDequeue: Vec[DecoupledIO[cacheLineDequeueBundle]] =
    Wire(Vec(param.memoryBankSize, Decoupled(new cacheLineDequeueBundle(param))))
  // 拼凑cache line
  queue.zipWithIndex.foreach { case (port, index) =>
    val (_, last, _, _) = firstlastHelper(burstSize, param.tlParam)(port.bits, port.fire)

    val cacheLineValid = RegInit(false.B)
    val dataShifterRegForPort = RegInit(0.U((param.cacheLineSize * 8).W))
    val cacheIndex = RegInit(0.U(param.cacheLineIndexBits.W))
    when(port.fire) {
      dataShifterRegForPort := (port.bits.data ## dataShifterRegForPort) >> param.tlParam.d.dataWidth
      cacheIndex := port.bits.source
    }
    lastCacheLineAck(index) := port.fire && (port.bits.source === cacheLineNumberReg)

    port.ready := !cacheLineValid
    cacheLineDequeue(index).valid := cacheLineValid
    cacheLineDequeue(index).bits.data := dataShifterRegForPort
    cacheLineDequeue(index).bits.index := cacheIndex

    when((port.fire & last) || cacheLineDequeue(index).fire) {
      cacheLineValid := port.fire
    }
  }

  // 接收拼凑出来的cache line
  // 对齐
  val alignedDequeue: DecoupledIO[cacheLineDequeueBundle] = Wire(Decoupled(new cacheLineDequeueBundle(param)))
  val unalignedCacheLine: ValidIO[cacheLineDequeueBundle] =
    RegInit(0.U.asTypeOf(Valid(new cacheLineDequeueBundle(param))))
  val unalignedEnqueueReady: Bool = alignedDequeue.ready || !unalignedCacheLine.valid

  val nextIndex: UInt = Mux(unalignedCacheLine.valid, unalignedCacheLine.bits.index + 1.U, 0.U)
  val nextCacheLineMatch: Seq[Bool] = cacheLineDequeue.map(deq => deq.valid && (deq.bits.index === nextIndex))
  cacheLineDequeue.zip(nextCacheLineMatch).foreach { case (d, r) =>
    d.ready := r && unalignedEnqueueReady
  }
  val nextData: UInt = Mux1H(nextCacheLineMatch, cacheLineDequeue.map(_.bits.data))
  val dataValid: Bool = VecInit(cacheLineDequeue.zip(nextCacheLineMatch).map {case (d, r) => d.valid && r }).asUInt.orR
  val unalignedEnqueueFire: Bool = dataValid && unalignedEnqueueReady

  val alignedDequeueValid: Bool =
    unalignedCacheLine.valid &&
      // 只有在base address 对齐的时候才需要推出最后一条访问的cache line
      (dataValid || ((unalignedCacheLine.bits.index === cacheLineNumberReg) && baseAddressAlignedReg))
  // update unalignedCacheLine
  when(unalignedEnqueueFire) {
    unalignedCacheLine.bits.data := nextData
    unalignedCacheLine.bits.index := nextIndex
  }

  when((unalignedEnqueueFire ^ alignedDequeue.fire) || lsuRequest.valid) {
    unalignedCacheLine.valid := unalignedEnqueueFire
  }

  alignedDequeue.valid := alignedDequeueValid
  alignedDequeue.bits.data :=
    multiShifter(right = true, multiSize = 8)(nextData ## unalignedCacheLine.bits.data, initOffset)
  alignedDequeue.bits.index := unalignedCacheLine.bits.index

  val bufferFull: Bool = RegInit(false.B)
  val bufferDequeueReady: Bool = Wire(Bool())
  val bufferDequeueFire: Bool = bufferDequeueReady && bufferFull

  alignedDequeue.ready := !bufferFull
  val bufferEnqueueSelect: UInt = Mux(
    alignedDequeue.fire,
    UIntToOH(cacheLineIndexInBuffer),
    0.U
  )

  dataBuffer.zipWithIndex.foreach {case (d, i) => when(bufferEnqueueSelect(i)) {d := alignedDequeue.bits.data}}
  val lastCacheLineForThisGroup: Bool = cacheLineIndexInBuffer === lsuRequestReg.instructionInformation.nf
  // update cacheLineIndexInBuffer
  when(alignedDequeue.fire || bufferDequeueFire) {
    cacheLineIndexInBuffer := Mux(bufferDequeueFire, 0.U, cacheLineIndexInBuffer + 1.U)
  }

  when((alignedDequeue.fire && lastCacheLineForThisGroup) || bufferDequeueFire) {
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
        val blockSize = 32 / dataBlockSize
        val nFiled = segSize + 1
        // 一次element会用掉多少 byte 数据
        val elementSize = dataBlockSize * nFiled
        VecInit(Seq.tabulate(8) { segIndex =>
          val res = Wire(UInt((param.cacheLineSize * 8).W))
          if (segIndex > segSize) {
            // todo: 优化这个 DontCare
            res := DontCare
          } else {
            val dataGroup: Seq[UInt] = Seq.tabulate(blockSize) { elementIndex =>
              val basePtr = elementSize * elementIndex + dataBlockSize * segIndex
              dataBuffer.asUInt((basePtr + dataBlockSize) * 8 - 1, basePtr * 8)
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
  val sendStateReg = RegEnable(initSendState, 0.U.asTypeOf(initSendState), bufferDequeueFire)
  when(bufferDequeueFire || (accessStateCheck && !lastPtr)) {
    accessState := Mux(bufferDequeueFire, initSendState, sendStateReg)
    accessPtr := Mux(bufferDequeueFire, lsuRequestReg.instructionInformation.nf, accessPtr - 1.U)
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
    }
  }

  val lastCacheRequest: Bool = lastRequest && tlPortA.fire
  val lastCacheRequestReg: Bool = RegEnable(lastCacheRequest, true.B, lastCacheRequest || validInstruction)
  val lastCacheLineAckReg: Bool = RegEnable(anyLastCacheLineAck, true.B, anyLastCacheLineAck || validInstruction)
  val bufferClear: Bool =
    !(
      // tile link port queue clear
      queue.map(_.valid).reduce(_ || _) ||
        // 拼cache line 的空了
        cacheLineDequeue.map(_.valid).reduce(_ || _) ||
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
}
