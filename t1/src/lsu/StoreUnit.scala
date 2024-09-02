// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lsu

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._
import chisel3.probe._
import chisel3.ltl._
import chisel3.ltl.Sequence._
import org.chipsalliance.t1.rtl.{cutUInt, multiShifter, EmptyBundle, VRFReadRequest}

class cacheLineEnqueueBundle(param: MSHRParam) extends Bundle {
  val data:  UInt = UInt((param.lsuTransposeSize * 8).W)
  val mask:  UInt = UInt(param.lsuTransposeSize.W)
  val index: UInt = UInt(param.cacheLineIndexBits.W)
}

@instantiable
class StoreUnit(param: MSHRParam) extends StrideBase(param) with LSUPublic {
  @public
  val memRequest: DecoupledIO[MemWrite] = IO(Decoupled(new MemWrite(param)))

  @public
  val status: LSUBaseStatus = IO(Output(new LSUBaseStatus))

  /** write channel to [[V]], which will redirect it to [[Lane.vrf]]. see [[LSU.vrfWritePort]]
    */
  @public
  val vrfReadDataPorts: Vec[DecoupledIO[VRFReadRequest]] = IO(
    Vec(
      param.laneNumber,
      Decoupled(new VRFReadRequest(param.regNumBits, param.vrfOffsetBits, param.instructionIndexBits))
    )
  )

  /** hard wire form Top. see [[LSU.vrfReadResults]]
    */
  @public
  val vrfReadResults:  Vec[UInt] = IO(Input(Vec(param.laneNumber, UInt(param.datapathWidth.W))))
  @public
  val vrfReadyToStore: Bool      = IO(Input(Bool()))
  @public
  val storeResponse = IO(Input(Bool()))

  // store unit probe
  @public
  val probe = IO(Output(Probe(new MemoryWriteProbe(param), layers.Verification)))

  // stage 0, 处理 vl, mask ...
  val dataGroupByteSize:           Int  = param.datapathWidth * param.laneNumber / 8
  val dataByteSize:                UInt = (csrInterface.vl << lsuRequest.bits.instructionInformation.eew).asUInt
  val lastDataGroupForInstruction: UInt = (dataByteSize >> log2Ceil(dataGroupByteSize)).asUInt -
    !dataByteSize(log2Ceil(dataGroupByteSize) - 1, 0).orR
  val lastDataGroupReg:            UInt = RegEnable(lastDataGroupForInstruction, 0.U, lsuRequest.valid)
  val nextDataGroup:               UInt = Mux(lsuRequest.valid, 0.U, dataGroup + 1.U)
  val isLastRead:                  Bool = dataGroup === lastDataGroupReg

  // stage1, 读vrf
  // todo: need hazardCheck?
  val hazardCheck:     Bool             = RegEnable(vrfReadyToStore && !lsuRequest.valid, false.B, lsuRequest.valid || vrfReadyToStore)
  // read stage dequeue ready need all source valid, Or add a queue to coordinate
  val vrfReadQueueVec: Seq[Queue[UInt]] =
    Seq.tabulate(param.laneNumber)(_ => Module(new Queue(UInt(param.datapathWidth.W), 2, flow = true, pipe = true)))

  // 从vrf里面读数据
  val readStageValid: Bool = Seq
    .tabulate(param.laneNumber) { laneIndex =>
      val readPort:  DecoupledIO[VRFReadRequest] = vrfReadDataPorts(laneIndex)
      val segPtr:    UInt                        = RegInit(0.U(3.W))
      val readCount: UInt                        = RegInit(0.U(dataGroupBits.W))
      val stageValid = RegInit(false.B)
      // queue for read latency
      val queue: Queue[UInt] = Module(new Queue(UInt(param.datapathWidth.W), param.vrfReadLatency, flow = true))

      val lastReadPtr: Bool = segPtr === 0.U

      val nextReadCount: UInt = Mux(lsuRequest.valid, 0.U(dataGroup.getWidth.W), readCount + 1.U)
      val lastReadGroup: Bool = readCount === lastDataGroupReg

      // update stageValid
      when((lsuRequest.valid && !invalidInstruction) || (lastReadGroup && lastReadPtr && readPort.fire)) {
        stageValid := lsuRequest.valid
      }

      // update segPtr
      when(lsuRequest.valid || readPort.fire) {
        segPtr := Mux(
          lsuRequest.valid,
          nfCorrection,
          Mux(
            lastReadPtr,
            lsuRequestReg.instructionInformation.nf,
            segPtr - 1.U
          )
        )
      }

      // update readCount
      when(lsuRequest.valid || (readPort.fire && lastReadPtr)) {
        readCount := nextReadCount
      }

      // vrf read request
      readPort.valid                 := stageValid && vrfReadQueueVec(laneIndex).io.enq.ready
      readPort.bits.vs               :=
        lsuRequestReg.instructionInformation.vs3 +
          segPtr * segmentInstructionIndexInterval +
          (readCount >> readPort.bits.offset.getWidth).asUInt
      readPort.bits.readSource       := 2.U
      readPort.bits.offset           := readCount
      readPort.bits.instructionIndex := lsuRequestReg.instructionIndex

      // pipe read fire
      val readResultFire = Pipe(readPort.fire, 0.U.asTypeOf(new EmptyBundle), param.vrfReadLatency).valid

      // latency queue enq
      queue.io.enq.valid := readResultFire
      queue.io.enq.bits  := vrfReadResults(laneIndex)
      AssertProperty(BoolSequence(!queue.io.enq.valid || queue.io.enq.ready))
      vrfReadQueueVec(laneIndex).io.enq <> queue.io.deq
      stageValid || RegNext(readPort.fire)
    }
    .reduce(_ || _)

  // stage buffer stage: data before regroup
  val bufferFull:               Bool      = RegInit(false.B)
  val accessBufferDequeueReady: Bool      = Wire(Bool())
  val accessBufferEnqueueReady: Bool      = !bufferFull || accessBufferDequeueReady
  val accessBufferEnqueueValid: Bool      = vrfReadQueueVec.map(_.io.deq.valid).reduce(_ && _)
  val readQueueClear:           Bool      = !vrfReadQueueVec.map(_.io.deq.valid).reduce(_ || _)
  val accessBufferEnqueueFire:  Bool      = accessBufferEnqueueValid && accessBufferEnqueueReady
  val lastPtr:                  Bool      = accessPtr === 0.U
  val lastPtrEnq:               Bool      = lastPtr && accessBufferEnqueueFire
  val accessBufferDequeueValid: Bool      = bufferFull || lastPtrEnq
  val accessBufferDequeueFire:  Bool      = accessBufferDequeueValid && accessBufferDequeueReady
  vrfReadQueueVec.foreach(_.io.deq.ready := accessBufferEnqueueFire)
  val accessDataUpdate:         Vec[UInt] =
    VecInit(VecInit(vrfReadQueueVec.map(_.io.deq.bits)).asUInt +: accessData.init)

  when(lastPtrEnq ^ accessBufferDequeueFire) {
    bufferFull := lastPtrEnq
  }
  when(accessBufferDequeueFire || accessBufferEnqueueFire || requestFireNext) {
    accessPtr  := Mux(
      accessBufferDequeueFire || lastPtr || requestFireNext,
      lsuRequestReg.instructionInformation.nf - (accessBufferEnqueueFire && !lastPtr),
      accessPtr - 1.U
    )
    // 在更新ptr的时候把数据推进 [[accessData]] 里面
    accessData := accessDataUpdate
  }

  // stage2, 用一个buffer来存转成cache line 的数据
  val bufferValid:               Bool      = RegInit(false.B)
  // 存每条cache 的mask, 也许能优化, 暂时先这样
  val maskForBufferData:         Vec[UInt] = RegInit(VecInit(Seq.fill(8)(0.U(param.lsuTransposeSize.W))))
  val maskForBufferDequeue:      UInt      = maskForBufferData(cacheLineIndexInBuffer)
  val lastDataGroupInDataBuffer: Bool      = RegInit(false.B)
  val alignedDequeueFire:        Bool      = memRequest.fire
  // cache 不对齐的时候的上一条残留
  val cacheLineTemp:             UInt      = RegEnable(dataBuffer.head, 0.U((param.lsuTransposeSize * 8).W), alignedDequeueFire)
  val maskTemp:                  UInt      = RegInit(0.U(param.lsuTransposeSize.W))
  val canSendTail:               Bool      = RegInit(false.B)
  val isLastCacheLineInBuffer:   Bool      = cacheLineIndexInBuffer === lsuRequestReg.instructionInformation.nf
  val bufferWillClear:           Bool      = alignedDequeueFire && isLastCacheLineInBuffer
  accessBufferDequeueReady := !bufferValid || (memRequest.ready && isLastCacheLineInBuffer)
  val bufferStageEnqueueData: Vec[UInt] = Mux(bufferFull, accessData, accessDataUpdate)
  // 处理mask, 对于 segment type 来说 一个mask 管 nf 个element
  val fillBySeg:              UInt      = Mux1H(
    UIntToOH(lsuRequestReg.instructionInformation.nf),
    Seq.tabulate(8) { segSize =>
      FillInterleaved(segSize + 1, maskForGroupWire)
    }
  )
  // 把数据regroup, 然后放去 [[dataBuffer]]
  when(accessBufferDequeueFire) {
    maskForBufferData         := cutUInt(fillBySeg, param.lsuTransposeSize)
    lastDataGroupInDataBuffer := isLastRead
    // todo: 只是因为参数恰好是一个方形的, 需要写一个反的
    dataBuffer                := Mux1H(
      dataEEWOH,
      Seq.tabulate(3) { sewSize =>
        // 每个数据块 2 ** sew byte
        val dataBlockBits = 8 << sewSize

        /** 先把数据按sew分组 bufferStageEnqueueData => [vx result, v(x + 1) result, ... vnf result, don't care ...] 分组
          * dataRegroupBySew => [ [vx_e0, vx_e1, ... vx_en], [vx1_e0, vx1_e1, ... vx1_en] ...] vx result = [vx_e0,
          * vx_e1, ... vx_en].asUInt
          */
        val dataRegroupBySew: Seq[Vec[UInt]] = bufferStageEnqueueData.map(cutUInt(_, dataBlockBits))
        Mux1H(
          UIntToOH(lsuRequestReg.instructionInformation.nf),
          Seq.tabulate(8) { segSize =>
            /** seg store 在mem 中的分布: vx_e0 vx1_e0 ... vnf_e0 vx_e1 vx1_e1 ... vnf_e1 所以我们把 [[dataRegroupBySew]] 的前 nf
              * 个组拿出来转置一下就得到了数据在 mem 中的分布情况
              */
            val dataInMem = VecInit(dataRegroupBySew.take(segSize + 1).transpose.map(VecInit(_).asUInt)).asUInt
            val regroupCacheLine: Vec[UInt] = cutUInt(dataInMem, param.lsuTransposeSize * 8)
            VecInit(Seq.tabulate(8) { segIndex =>
              val res = Wire(UInt((param.lsuTransposeSize * 8).W))
              if (segIndex > segSize) {
                // todo: 优化这个 DontCare
                res := DontCare
              } else {
                res := regroupCacheLine(segIndex)
              }
              res
            }).asUInt.suggestName(s"regroupLoadData_${sewSize}_$segSize")
          }
        )
      }
    ).asTypeOf(dataBuffer)
  }.elsewhen(alignedDequeueFire) {
    dataBuffer := VecInit(dataBuffer.tail :+ 0.U.asTypeOf(dataBuffer.head))
  }

  // update mask
  when(lsuRequest.valid || accessBufferDequeueFire) {
    maskCounterInGroup := Mux(isLastDataGroup || lsuRequest.valid, 0.U, nextMaskCount)
    when(isLastDataGroup && !isLastMaskGroup) {
      maskSelect.valid := true.B
    }
    when((isLastDataGroup && !isLastMaskGroup) || lsuRequest.valid) {
      maskGroupCounter := Mux(lsuRequest.valid, 0.U, nextMaskGroup)
    }
    dataGroup          := nextDataGroup
  }

  when(accessBufferDequeueFire || alignedDequeueFire) {
    cacheLineIndexInBuffer := Mux(accessBufferDequeueFire, 0.U, cacheLineIndexInBuffer + 1.U)
  }

  when(accessBufferDequeueFire ^ bufferWillClear) {
    bufferValid := accessBufferDequeueFire
  }

  when(lsuRequest.valid || alignedDequeueFire) {
    bufferBaseCacheLineIndex := Mux(lsuRequest.valid, 0.U, bufferBaseCacheLineIndex + 1.U)
  }

  when(lsuRequest.valid || alignedDequeueFire) {
    maskTemp    := Mux(lsuRequest.valid, 0.U, maskForBufferDequeue)
    canSendTail := !lsuRequest.valid && bufferValid && isLastCacheLineInBuffer && lastDataGroupInDataBuffer
  }

  // 连接 alignedDequeue
  val needSendTail: Bool = bufferBaseCacheLineIndex === cacheLineNumberReg
  memRequest.valid     := bufferValid || (canSendTail && needSendTail)
  // aligned
  memRequest.bits.data :=
    multiShifter(right = false, multiSize = 8)(dataBuffer.head ## cacheLineTemp, initOffset) >> cacheLineTemp.getWidth
  val selectMaskForTail: UInt = Mux(bufferValid, maskForBufferDequeue, 0.U(maskTemp.getWidth.W))
  memRequest.bits.mask  := ((selectMaskForTail ## maskTemp) << initOffset) >> maskTemp.getWidth
  memRequest.bits.index := bufferBaseCacheLineIndex

  // select by address set
  val alignedDequeueAddress: UInt =
    ((lsuRequestReg.rs1Data >> param.cacheLineBits).asUInt + bufferBaseCacheLineIndex) ##
      0.U(param.cacheLineBits.W)
  memRequest.bits.address := alignedDequeueAddress

  val addressQueueSize: Int         = (param.vLen * 8) / (param.datapathWidth * param.laneNumber) + 1
  // address Wait For Response
  val addressQueue:     Queue[UInt] = Module(new Queue[UInt](UInt(param.paWidth.W), addressQueueSize))
  addressQueue.io.enq.valid := memRequest.fire
  addressQueue.io.enq.bits  := alignedDequeueAddress
  addressQueue.io.deq.ready := storeResponse

  status.idle := !bufferValid && !readStageValid && readQueueClear && !bufferFull && !addressQueue.io.deq.valid
  val idleNext: Bool = RegNext(status.idle, true.B)
  status.last             := (!idleNext && status.idle) || invalidInstructionNext
  status.changeMaskGroup  := maskSelect.valid && !lsuRequest.valid
  status.instructionIndex := lsuRequestReg.instructionIndex
  status.startAddress     := Mux(addressQueue.io.deq.valid, addressQueue.io.deq.bits, alignedDequeueAddress)
  status.endAddress       := ((lsuRequestReg.rs1Data >> param.cacheLineBits).asUInt + cacheLineNumberReg) ##
    0.U(param.cacheLineBits.W)
  dontTouch(status)

  // Store Unit is idle
  @public
  val idleProbe = IO(Output(Probe(Bool(), layers.Verification)))

  // lsuRequest is valid
  @public
  val lsuRequestValidProbe = IO(Output(Probe(Bool(), layers.Verification)))

//  @public
//  val tlPortAIsValidProbe = Seq.fill(param.memoryBankSize)(IO(Output(Probe(Bool()))))
//  @public
//  val tlPortAIsReadyProbe = Seq.fill(param.memoryBankSize)(IO(Output(Probe(Bool()))))
//  tlPortA.zipWithIndex.foreach({ case(port, i) =>
//    define(tlPortAIsValidProbe(i), ProbeValue(port.valid))
//    define(tlPortAIsReadyProbe(i), ProbeValue(port.ready))
//  })

  @public
  val addressConflictProbe = IO(Output(Probe(Bool(), layers.Verification)))

  @public
  val vrfReadDataPortIsValidProbe = Seq.fill(param.laneNumber)(IO(Output(Probe(Bool(), layers.Verification))))
  @public
  val vrfReadDataPortIsReadyProbe = Seq.fill(param.laneNumber)(IO(Output(Probe(Bool(), layers.Verification))))

  @public
  val vrfReadyToStoreProbe = IO(Output(Probe(Bool(), layers.Verification)))

  layer.block(layers.Verification) {
    val probeWire = Wire(new MemoryWriteProbe(param))
    define(probe, ProbeValue(probeWire))
    probeWire.valid   := alignedDequeueFire
    probeWire.index   := 1.U
    probeWire.data    := memRequest.bits.data
    probeWire.mask    := memRequest.bits.mask
    probeWire.address := alignedDequeueAddress

    define(idleProbe, ProbeValue(status.idle))
    define(lsuRequestValidProbe, ProbeValue(lsuRequest.valid))
    define(addressConflictProbe, ProbeValue(addressConflict))
    vrfReadDataPorts.zipWithIndex.foreach({ case (port, i) =>
      define(vrfReadDataPortIsValidProbe(i), ProbeValue(port.valid))
      define(vrfReadDataPortIsReadyProbe(i), ProbeValue(port.ready))
    })
    define(vrfReadyToStoreProbe, ProbeValue(vrfReadyToStore))
  }
}
