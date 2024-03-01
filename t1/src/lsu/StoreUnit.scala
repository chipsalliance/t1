// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lsu

import chisel3._
import chisel3.util._
import chisel3.probe._
import org.chipsalliance.t1.rtl.{EmptyBundle, VRFReadRequest, cutUInt, multiShifter}
import tilelink.TLChannelA

class cacheLineEnqueueBundle(param: MSHRParam) extends Bundle {
  val data: UInt = UInt((param.lsuTransposeSize * 8).W)
  val mask: UInt = UInt(param.lsuTransposeSize.W)
  val index: UInt = UInt(param.cacheLineIndexBits.W)
}

class StoreUnit(param: MSHRParam) extends StrideBase(param) with LSUPublic {
  val tlPortA: Vec[DecoupledIO[TLChannelA]] = IO(Vec(param.memoryBankSize, param.tlParam.bundle().a))

  val status: StoreStatus = IO(Output(new StoreStatus(param.memoryBankSize)))

  /** write channel to [[V]], which will redirect it to [[Lane.vrf]].
   * see [[LSU.vrfWritePort]]
   */
  val vrfReadDataPorts: Vec[DecoupledIO[VRFReadRequest]] = IO(
    Vec(
      param.laneNumber,
      Decoupled(new VRFReadRequest(param.regNumBits, param.vrfOffsetBits, param.instructionIndexBits))
    )
  )

  /** hard wire form Top.
   * see [[LSU.vrfReadResults]]
   */
  val vrfReadResults: Vec[UInt] = IO(Input(Vec(param.laneNumber, UInt(param.datapathWidth.W))))
  val vrfReadyToStore: Bool = IO(Input(Bool()))

  // stage 0, 处理 vl, mask ...
  val dataGroupByteSize: Int = param.datapathWidth * param.laneNumber / 8
  val dataByteSize: UInt = (csrInterface.vl << lsuRequest.bits.instructionInformation.eew).asUInt
  val lastDataGroupForInstruction: UInt = (dataByteSize >> log2Ceil(dataGroupByteSize)).asUInt -
    !dataByteSize(log2Ceil(dataGroupByteSize) - 1, 0).orR
  val lastDataGroupReg: UInt = RegEnable(lastDataGroupForInstruction, 0.U, lsuRequest.valid)
  val nextDataGroup: UInt = Mux(lsuRequest.valid, 0.U, dataGroup + 1.U)
  val isLastRead: Bool = dataGroup === lastDataGroupReg
  val lastGroupAndNeedAlign: Bool = initOffset.orR && isLastRead

  // stage1, 读vrf
  // todo: need hazardCheck?
  val hazardCheck: Bool = RegEnable(vrfReadyToStore && !lsuRequest.valid, false.B, lsuRequest.valid || vrfReadyToStore)
  // read stage dequeue ready need all source valid, Or add a queue to coordinate
  val vrfReadQueueVec: Seq[Queue[UInt]] =
    Seq.tabulate(param.laneNumber)(_ => Module(new Queue(UInt(param.datapathWidth.W), 2, flow = true, pipe = true)))

  // 从vrf里面读数据
  val readStageValid: Bool = Seq.tabulate(param.laneNumber) { laneIndex =>
    val readPort: DecoupledIO[VRFReadRequest] = vrfReadDataPorts(laneIndex)
    val segPtr: UInt = RegInit(0.U(3.W))
    val readCount: UInt = RegInit(0.U(dataGroupBits.W))
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
        lsuRequest.bits.instructionInformation.nf,
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
    readPort.valid := stageValid && vrfReadQueueVec(laneIndex).io.enq.ready
    readPort.bits.vs :=
      lsuRequestReg.instructionInformation.vs3 +
        segPtr * segmentInstructionIndexInterval +
        (readCount >> readPort.bits.offset.getWidth).asUInt
    readPort.bits.readSource := 2.U
    readPort.bits.offset := readCount
    readPort.bits.instructionIndex := lsuRequestReg.instructionIndex

    // pipe read fire
    val readResultFire = Pipe(readPort.fire, 0.U.asTypeOf(new EmptyBundle), param.vrfReadLatency).valid

    // latency queue enq
    queue.io.enq.valid := readResultFire
    queue.io.enq.bits := vrfReadResults(laneIndex)
    assert(!queue.io.enq.valid || queue.io.enq.ready)

    vrfReadQueueVec(laneIndex).io.enq <> queue.io.deq
    stageValid
  }.reduce(_ || _)

  // stage buffer stage: data before regroup
  val bufferFull: Bool = RegInit(false.B)
  val accessBufferDequeueReady: Bool = Wire(Bool())
  val accessBufferEnqueueReady: Bool = !bufferFull || accessBufferDequeueReady
  val accessBufferEnqueueValid: Bool = vrfReadQueueVec.map(_.io.deq.valid).reduce(_ && _)
  val readQueueClear: Bool = !vrfReadQueueVec.map(_.io.deq.valid).reduce(_ || _)
  val accessBufferEnqueueFire: Bool = accessBufferEnqueueValid && accessBufferEnqueueReady
  val lastPtr: Bool = accessPtr === 0.U
  val lastPtrEnq: Bool = lastPtr && accessBufferEnqueueFire
  val accessBufferDequeueValid: Bool = bufferFull || lastPtrEnq
  val accessBufferDequeueFire: Bool = accessBufferDequeueValid && accessBufferDequeueReady
  vrfReadQueueVec.foreach(_.io.deq.ready := accessBufferEnqueueFire)
  val accessDataUpdate: Vec[UInt] =
    VecInit(VecInit(vrfReadQueueVec.map(_.io.deq.bits)).asUInt +: accessData.init)

  when(lastPtrEnq ^ accessBufferDequeueFire) {
    bufferFull := lastPtrEnq
  }
  when(accessBufferDequeueFire || accessBufferEnqueueFire || requestFireNext) {
    accessPtr := Mux(
      accessBufferDequeueFire || lastPtr || requestFireNext,
      lsuRequestReg.instructionInformation.nf - (accessBufferEnqueueFire && !lastPtr),
      accessPtr - 1.U
    )
    // 在更新ptr的时候把数据推进 [[accessData]] 里面
    accessData := accessDataUpdate
  }

  // stage2, 用一个buffer来存转成cache line 的数据
  val bufferValid: Bool = RegInit(false.B)
  // 存每条cache 的mask, 也许能优化, 暂时先这样
  val maskForBufferData: Vec[UInt] = RegInit(VecInit(Seq.fill(8)(0.U(param.lsuTransposeSize.W))))
  val maskForBufferDequeue: UInt = maskForBufferData(cacheLineIndexInBuffer)
  val tailLeft2: Bool = RegInit(false.B)
  val alignedDequeue: DecoupledIO[cacheLineEnqueueBundle] = Wire(Decoupled(new cacheLineEnqueueBundle(param)))
  val alignedDequeueFire: Bool = alignedDequeue.fire
  // cache 不对齐的时候的上一条残留
  val cacheLineTemp: UInt = RegEnable(dataBuffer.head, 0.U((param.lsuTransposeSize * 8).W), alignedDequeueFire)
  val maskTemp: UInt = RegInit(0.U(param.lsuTransposeSize.W))
  val tailValid: Bool = RegInit(false.B)
  val isLastCacheLineInBuffer: Bool = cacheLineIndexInBuffer === lsuRequestReg.instructionInformation.nf
  val bufferWillClear: Bool = alignedDequeueFire && isLastCacheLineInBuffer
  accessBufferDequeueReady := !bufferValid || (alignedDequeue.ready && isLastCacheLineInBuffer)
  val bufferStageEnqueueData: Vec[UInt] = Mux(bufferFull, accessData, accessDataUpdate)
  // 处理mask, 对于 segment type 来说 一个mask 管 nf 个element
  val fillBySeg: UInt = Mux1H(UIntToOH(lsuRequestReg.instructionInformation.nf), Seq.tabulate(8) { segSize =>
    FillInterleaved(segSize + 1, maskForGroupWire)
  })
  // 把数据regroup, 然后放去 [[dataBuffer]]
  when(accessBufferDequeueFire) {
    maskForBufferData := cutUInt(fillBySeg, param.lsuTransposeSize)
    tailLeft2 := lastGroupAndNeedAlign
    // todo: 只是因为参数恰好是一个方形的, 需要写一个反的
    dataBuffer := Mux1H(dataEEWOH, Seq.tabulate(3) { sewSize =>
      // 每个数据块 2 ** sew byte
      val dataBlockBits = 8 << sewSize

      /** 先把数据按sew分组
       * bufferStageEnqueueData => [vx result, v(x + 1) result, ... vnf result, don't care ...]
       * 分组
       * dataRegroupBySew => [ [vx_e0, vx_e1, ... vx_en], [vx1_e0, vx1_e1, ... vx1_en] ...]
       *  vx result = [vx_e0, vx_e1, ... vx_en].asUInt
       * */
      val dataRegroupBySew: Seq[Vec[UInt]] = bufferStageEnqueueData.map(cutUInt(_, dataBlockBits))
      Mux1H(UIntToOH(lsuRequestReg.instructionInformation.nf), Seq.tabulate(8) { segSize =>
        /** seg store 在mem 中的分布:
         *  vx_e0 vx1_e0 ... vnf_e0  vx_e1 vx1_e1 ... vnf_e1
         *  所以我们把 [[dataRegroupBySew]] 的前 nf 个组拿出来转置一下就得到了数据在 mem 中的分布情况
         * */
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
      })
    }).asTypeOf(dataBuffer)
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
    dataGroup := nextDataGroup
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
    maskTemp := Mux(lsuRequest.valid, 0.U, maskForBufferDequeue)
    tailValid := Mux(lsuRequest.valid, false.B, bufferValid && tailLeft2 && isLastCacheLineInBuffer)
  }

  // 连接 alignedDequeue
  alignedDequeue.valid := bufferValid || tailValid
  // aligned
  alignedDequeue.bits.data :=
    multiShifter(right = false, multiSize = 8)(dataBuffer.head ## cacheLineTemp, initOffset) >> cacheLineTemp.getWidth
  val selectMaskForTail: UInt = Mux(bufferValid, maskForBufferDequeue, 0.U(maskTemp.getWidth.W))
  alignedDequeue.bits.mask := ((selectMaskForTail ## maskTemp) << initOffset) >> maskTemp.getWidth
  alignedDequeue.bits.index := bufferBaseCacheLineIndex

  // select by address set
  val alignedDequeueAddress: UInt = ((lsuRequestReg.rs1Data >> param.cacheLineBits).asUInt + bufferBaseCacheLineIndex) ##
    0.U(param.cacheLineBits.W)
  val selectOH: UInt = VecInit(param.banks.map(bs => bs.region.matches(alignedDequeueAddress))).asUInt
  assert(PopCount(selectOH) === 1.U, "address overlap")
  val currentAddress: Vec[UInt] = Wire(Vec(param.memoryBankSize, UInt(param.tlParam.a.addressWidth.W)))
  val sendStageReady: Vec[Bool] = Wire(Vec(param.memoryBankSize, Bool()))
  // tl 发送单元
  val readyVec = Seq.tabulate(param.memoryBankSize) { portIndex =>
    val dataToSend: ValidIO[cacheLineEnqueueBundle] = RegInit(0.U.asTypeOf(Valid(new cacheLineEnqueueBundle(param))))
    val addressReg: UInt = RegInit(0.U(param.paWidth.W))
    val port: DecoupledIO[TLChannelA] = tlPortA(portIndex)
    val portFire: Bool = port.fire
    val burstIndex: UInt = RegInit(0.U(log2Ceil(burstSize).W))
    val burstOH: UInt = UIntToOH(burstIndex)
    val last = burstIndex.andR
    val enqueueReady: Bool = !dataToSend.valid || (port.ready && !addressConflict && last)
    val enqueueFire: Bool = enqueueReady && alignedDequeue.valid && selectOH(portIndex)
    val firstCacheLine = RegEnable(lsuRequest.valid, true.B, lsuRequest.valid || enqueueFire)
    currentAddress(portIndex) := Mux(firstCacheLine, 0.U, addressReg)
    status.releasePort(portIndex) := burstIndex === 0.U
    when(enqueueFire) {
      dataToSend.bits := alignedDequeue.bits
      addressReg := alignedDequeueAddress
    }

    when(enqueueFire ^ (portFire && last)) {
      dataToSend.valid := enqueueFire
    }

    when(enqueueFire || portFire) {
      burstIndex := Mux(enqueueFire, 0.U, burstIndex + 1.U)
    }

    port.valid := dataToSend.valid && !addressConflict
    port.bits.opcode := 0.U
    port.bits.param := 0.U
    port.bits.size := param.cacheLineBits.U
    port.bits.source := dataToSend.bits.index
    port.bits.address := addressReg
    port.bits.mask := Mux1H(
      burstOH,
      Seq.tabulate(burstSize)(burstIndex => dataToSend.bits.mask(
        (burstIndex + 1) * param.tlParam.a.maskWidth - 1,
        burstIndex * param.tlParam.a.maskWidth,
      ))
    )
    port.bits.data := Mux1H(
      burstOH,
      Seq.tabulate(burstSize)(burstIndex => dataToSend.bits.data(
        (burstIndex + 1) * param.tlParam.a.dataWidth - 1,
        burstIndex * param.tlParam.a.dataWidth,
      ))
    )
    port.bits.corrupt := false.B
    sendStageReady(portIndex) := enqueueReady
    !dataToSend.valid
  }

  val sendStageClear: Bool = readyVec.reduce(_ && _)
  alignedDequeue.ready := (sendStageReady.asUInt & selectOH).orR

  status.idle := sendStageClear && !bufferValid && !readStageValid && readQueueClear && !bufferFull
  val idleNext: Bool = RegNext(status.idle, true.B)
  status.last := (!idleNext && status.idle) || invalidInstructionNext
  status.changeMaskGroup := maskSelect.valid && !lsuRequest.valid
  status.instructionIndex := lsuRequestReg.instructionIndex
  status.startAddress := Mux1H(selectOH, currentAddress)
  status.endAddress := ((lsuRequestReg.rs1Data >> param.cacheLineBits).asUInt + cacheLineNumberReg) ##
    0.U(param.cacheLineBits.W)
  dontTouch(status)

  /**
    * Probes
    */
  // Store Unit is idle
  val idleProbe = IO(Output(Probe(Bool())))
  define(idleProbe, ProbeValue(status.idle))

  // lsuRequest is valid
  val lsuRequestValidProbe = IO(Output(Probe(Bool())))
  define(lsuRequestValidProbe, ProbeValue(lsuRequest.valid))

  val tlPortAIsValidProbe = Seq.fill(param.memoryBankSize)(IO(Output(Probe(Bool()))))
  val tlPortAIsReadyProbe = Seq.fill(param.memoryBankSize)(IO(Output(Probe(Bool()))))
  tlPortA.zipWithIndex.foreach({ case(port, i) =>
    define(tlPortAIsValidProbe(i), ProbeValue(port.valid))
    define(tlPortAIsReadyProbe(i), ProbeValue(port.ready))
  })

  val addressConflictProbe = IO(Output(Probe(Bool())))
  define(addressConflictProbe, ProbeValue(addressConflict))

  val vrfReadDataPortIsValidProbe = Seq.fill(param.laneNumber)(IO(Output(Probe(Bool()))))
  val vrfReadDataPortIsReadyProbe = Seq.fill(param.laneNumber)(IO(Output(Probe(Bool()))))
  vrfReadDataPorts.zipWithIndex.foreach({ case(port, i) =>
    define(vrfReadDataPortIsValidProbe(i), ProbeValue(port.valid))
    define(vrfReadDataPortIsReadyProbe(i), ProbeValue(port.ready))
  })

  val vrfReadyToStoreProbe = IO(Output(Probe(Bool())))
  define(vrfReadyToStoreProbe, ProbeValue(vrfReadyToStore))

  val alignedDequeueValidProbe = IO(Output(Probe(Bool())))
  define(alignedDequeueValidProbe, ProbeValue(alignedDequeue.valid))
  val alignedDequeueReadyProbe = IO(Output(Probe(Bool())))
  define(alignedDequeueReadyProbe, ProbeValue(alignedDequeue.ready))
}
