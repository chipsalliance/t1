// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lane

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._
import org.chipsalliance.dwbb.stdlib.queue
import org.chipsalliance.dwbb.stdlib.queue.{Queue, QueueIO}
import org.chipsalliance.t1.rtl._
import org.chipsalliance.t1.rtl.decoder.Decoder

class PipeForMaskUnit(parameter: LaneParameter) extends Bundle {
  val sew1H:         UInt = UInt(3.W)
  val source1:       UInt = UInt(parameter.datapathWidth.W)
  val source2:       UInt = UInt(parameter.datapathWidth.W)
  val readFromScala: UInt = UInt(parameter.datapathWidth.W)
  val vl:            UInt = UInt(parameter.vlMaxBits.W)
  val vlmul:         UInt = UInt(3.W)
}

class MaskExchangeRelease extends Bundle {
  val maskPipe: Bool = Bool()
}

class MaskPipeBundle(parameter: LaneParameter) extends Bundle {
  val req      = new LaneStage3Enqueue(parameter, true)
  val maskPipe = new PipeForMaskUnit(parameter)
}

class CrossWritePipe(parameter: LaneParameter) extends Bundle {
  val data:         UInt = UInt(parameter.datapathWidth.W)
  val groupCounter: UInt = UInt(parameter.groupNumberBits.W)
  val mask:         UInt = UInt((parameter.datapathWidth / 8).W)
}

class SlideRequest0(parameter: LaneParameter) extends Bundle {
  val address: UInt = UInt(parameter.eLen.W)
  val data:    UInt = UInt(parameter.eLen.W)
}

class SlideRequest1(parameter: LaneParameter) extends Bundle {
  val sink:         UInt = UInt(parameter.laneNumberBits.W)
  val data:         UInt = UInt(parameter.eLen.W)
  val mask:         UInt = UInt((parameter.eLen / 8).W)
  val groupCounter: UInt = UInt(parameter.groupNumberBits.W)
}

class GatherRequest0(parameter: LaneParameter) extends Bundle {
  val executeIndex: UInt = UInt(parameter.vlMaxBits.W)
  val readIndex:    UInt = UInt(parameter.eLen.W)
}

class GatherRequest1(datapathWidth: Int, groupNumberBits: Int, laneNumberBits: Int, eLen: Int) extends Bundle {
  val readSink:    UInt = UInt(laneNumberBits.W)
  val readCounter: UInt = UInt(groupNumberBits.W)
  val readOffset:  UInt = UInt(log2Ceil(datapathWidth / 8).W)
  val skipRead:    Bool = Bool()

  val writeSink:    UInt = UInt(laneNumberBits.W)
  val writeCounter: UInt = UInt(groupNumberBits.W)
  val writeOffset:  UInt = UInt(log2Ceil(datapathWidth / 8).W)
  val mask:         UInt = UInt((eLen / 8).W)
}

@instantiable
class MaskExchangeUnit(parameter: LaneParameter) extends Module {
  @public
  val enqueue: DecoupledIO[LaneStage3Enqueue] =
    IO(Flipped(Decoupled(new LaneStage3Enqueue(parameter, true))))

  @public
  val pipeForMask: PipeForMaskUnit = IO(Input(new PipeForMaskUnit(parameter)))

  @public
  val dequeue: DecoupledIO[LaneStage3Enqueue] =
    IO(Decoupled(new LaneStage3Enqueue(parameter, true)))

  @public
  val maskReq: DecoupledIO[MaskUnitExeReq] = IO(
    Decoupled(
      new MaskUnitExeReq(parameter.eLen, parameter.datapathWidth, parameter.instructionIndexBits, parameter.fpuEnable)
    )
  )

  @public
  val maskRequestToLSU: Bool = IO(Output(Bool()))

  @public
  val crossWritePort2Deq: Vec[DecoupledIO[WriteBusData]] =
    IO(
      Vec(
        2,
        Decoupled(
          new WriteBusData(
            parameter.datapathWidth,
            parameter.instructionIndexBits,
            parameter.groupNumberBits
          )
        )
      )
    )

  @public
  val crossWritePort2Enq: Vec[DecoupledIO[WriteBusData]] =
    IO(
      Vec(
        2,
        Flipped(
          Decoupled(
            new WriteBusData(
              parameter.datapathWidth,
              parameter.instructionIndexBits,
              parameter.groupNumberBits
            )
          )
        )
      )
    )

  @public
  val crossWritePort4Deq: Vec[DecoupledIO[WriteBusData]] =
    IO(
      Vec(
        4,
        Decoupled(
          new WriteBusData(
            parameter.datapathWidth,
            parameter.instructionIndexBits,
            parameter.groupNumberBits
          )
        )
      )
    )

  @public
  val crossWritePort4Enq: Vec[DecoupledIO[WriteBusData]] =
    IO(
      Vec(
        4,
        Flipped(
          Decoupled(
            new WriteBusData(
              parameter.datapathWidth,
              parameter.instructionIndexBits,
              parameter.groupNumberBits
            )
          )
        )
      )
    )

  @public
  val freeCrossDataDeq: DecoupledIO[FreeWriteBusData] =
    IO(
      Decoupled(
        new FreeWriteBusData(
          parameter.datapathWidth,
          parameter.groupNumberBits,
          parameter.laneNumberBits
        )
      )
    )

  @public
  val freeCrossDataEnq: DecoupledIO[FreeWriteBusData] =
    IO(
      Flipped(
        Decoupled(
          new FreeWriteBusData(
            parameter.datapathWidth,
            parameter.groupNumberBits,
            parameter.laneNumberBits
          )
        )
      )
    )

  @public
  val freeCrossReqDeq: DecoupledIO[FreeWriteBusRequest] =
    IO(
      Decoupled(
        new FreeWriteBusRequest(
          parameter.datapathWidth,
          parameter.groupNumberBits,
          parameter.laneNumberBits
        )
      )
    )

  @public
  val freeCrossReqEnq: DecoupledIO[FreeWriteBusRequest] =
    IO(
      Flipped(
        Decoupled(
          new FreeWriteBusRequest(
            parameter.datapathWidth,
            parameter.groupNumberBits,
            parameter.laneNumberBits
          )
        )
      )
    )

  @public
  val maskPipeRelease: MaskExchangeRelease = IO(Output(new MaskExchangeRelease))

  @public
  val laneIndex: UInt = IO(Input(UInt(parameter.laneNumberBits.W)))

  // todo: sSendResponse -> sendResponse
  val enqIsMaskRequest: Bool = !enqueue.bits.sSendResponse
  // not maskUnit && not send out
  val enqSendToDeq:     Bool =
    !enqueue.bits.decodeResult(Decoder.maskUnit) && enqueue.bits.sSendResponse && !enqueue.bits.decodeResult(
      Decoder.maskPipeType
    )
  val enqSendMaskPipe:  Bool = enqueue.bits.decodeResult(Decoder.maskPipeType)
  val enqFFoIndex:      Bool = enqueue.bits.decodeResult(Decoder.ffo) &&
    enqueue.bits.decodeResult(Decoder.targetRd)

  // todo: connect mask request & response
  maskReq.valid        := enqIsMaskRequest && enqueue.valid
  maskReq.bits.source1 := enqueue.bits.pipeData
  val ffoIndexDataExtend: UInt = VecInit(cutUIntBySize(enqueue.bits.ffoIndex, parameter.laneScale).map { d =>
    changeUIntSize(d, parameter.eLen)
  }).asUInt
  maskReq.bits.source2          := Mux(
    enqFFoIndex,
    ffoIndexDataExtend,
    enqueue.bits.data
  )
  maskReq.bits.index            := enqueue.bits.instructionIndex
  maskReq.bits.ffo              := enqueue.bits.ffoSuccess
  maskReq.bits.maskRequestToLSU := enqueue.bits.loadStore

  maskReq.bits.fpReduceValid.zip(enqueue.bits.fpReduceValid).foreach { case (sink, source) => sink := source }

  maskRequestToLSU := enqueue.bits.loadStore

  // mask pipe request queue
  val maskReqQueue: queue.QueueIO[MaskPipeBundle] =
    Queue.io(new MaskPipeBundle(parameter), parameter.maskRequestQueueSize)
  maskReqQueue.enq.valid         := enqueue.valid && enqSendMaskPipe
  maskPipeRelease.maskPipe       := maskReqQueue.deq.fire
  maskReqQueue.enq.bits.req      := enqueue.bits
  maskReqQueue.enq.bits.maskPipe := pipeForMask

  // opcode (0, 1) cross write 2/4
  val crossWriteState: UInt = RegInit(15.U(4.W))

  // todo: other type
  val maskPipeDeqReady:   Bool              = Wire(Bool())
  val maskPipeEnqReq:     LaneStage3Enqueue = maskReqQueue.deq.bits.req
  val maskPipeReqReg:     LaneStage3Enqueue = RegInit(0.U.asTypeOf(maskPipeEnqReq))
  val maskPipeMessageReg: PipeForMaskUnit   = RegInit(0.U.asTypeOf(new PipeForMaskUnit(parameter)))
  val rxGroupIndex:       UInt              = RegInit(0.U(parameter.groupNumberBits.W))

  val maskPipeValid:   Bool      = RegInit(false.B)
  val crossWriteFire2: Vec[Bool] = Wire(Vec(2, Bool()))
  val crossWriteFire4: Vec[Bool] = Wire(Vec(4, Bool()))
  val crossWriteDeqFire = crossWriteFire4.asUInt | crossWriteFire2.asUInt

  val maskPipeEnqIsExtend: Bool = maskPipeEnqReq.decodeResult(Decoder.maskPipeUop) === BitPat("b0000?")
  val maskPipeEnqIsGather: Bool = maskPipeEnqReq.decodeResult(Decoder.maskPipeUop) === BitPat("b0001?")
  val gather16:            Bool = maskPipeEnqReq.decodeResult(Decoder.maskPipeUop) === BitPat("b00011")
  val maskPipeEnqIsSlid:   Bool = maskPipeEnqReq.decodeResult(Decoder.maskPipeUop) === BitPat("b001??")

  val maskPipeDeqFire = maskPipeValid && maskPipeDeqReady
  val maskPipeEnqFire = maskReqQueue.deq.fire
  when(maskPipeDeqFire ^ maskPipeEnqFire) {
    maskPipeValid := maskPipeEnqFire
  }

  maskReqQueue.deq.ready := !maskPipeValid || maskPipeDeqReady
  val opcode1H: UInt = UIntToOH(maskPipeReqReg.decodeResult(Decoder.maskPipeUop))
  // update register
  when(maskPipeEnqFire) {
    maskPipeReqReg     := maskPipeEnqReq
    maskPipeMessageReg := maskReqQueue.deq.bits.maskPipe
    when(maskPipeEnqIsExtend) {
      crossWriteState := Mux(maskPipeEnqReq.decodeResult(Decoder.maskPipeUop)(0), 0.U, 12.U)
    }
    when(maskPipeEnqReq.instructionIndex =/= maskPipeReqReg.instructionIndex) {
      rxGroupIndex := 0.U
    }
  }
  when(crossWriteDeqFire.orR) {
    crossWriteState := crossWriteState | crossWriteDeqFire
  }

  val extendData2:     UInt      = Mux(
    maskPipeMessageReg.sew1H(2),
    VecInit(
      cutUInt(maskPipeReqReg.data, 16).map(d =>
        changeUIntSizeWidthSign(d, 32, !maskPipeReqReg.decodeResult(Decoder.unsigned1))
      )
    ).asUInt,
    VecInit(
      cutUInt(maskPipeReqReg.data, 8).map(d =>
        changeUIntSizeWidthSign(d, 16, !maskPipeReqReg.decodeResult(Decoder.unsigned1))
      )
    ).asUInt
  )
  val crossWriteData2: Vec[UInt] = Mux(
    maskPipeReqReg.decodeResult(Decoder.extend),
    cutUIntBySize(extendData2, 2),
    maskPipeReqReg.crossWriteData
  )
  // VRF cross write
  crossWritePort2Deq.zipWithIndex.foreach { case (port, index) =>
    port.valid                 := maskPipeValid && !crossWriteState(index) && opcode1H(0)
    port.bits.mask             := cutUIntBySize(maskPipeReqReg.mask, 2)(index)
    port.bits.data             := crossWriteData2(index)
    port.bits.counter          := maskPipeReqReg.groupCounter
    port.bits.instructionIndex := maskPipeReqReg.instructionIndex
    crossWriteFire2(index)     := port.fire
  }

  val extendData4: UInt = VecInit(
    cutUInt(maskPipeReqReg.data, 8).map(d =>
      changeUIntSizeWidthSign(d, 32, !maskPipeReqReg.decodeResult(Decoder.unsigned1))
    )
  ).asUInt
  // VRF cross write
  crossWritePort4Deq.zipWithIndex.foreach { case (port, index) =>
    port.valid                 := maskPipeValid && !crossWriteState(index) && opcode1H(1)
    port.bits.mask             := cutUIntBySize(maskPipeReqReg.mask, 4)(index)
    port.bits.data             := cutUIntBySize(extendData4, 4)(index)
    port.bits.counter          := maskPipeReqReg.groupCounter
    port.bits.instructionIndex := maskPipeReqReg.instructionIndex
    crossWriteFire4(index)     := port.fire
  }

  // cross write rx

  /** queue for cross lane writing. TODO: benchmark the size of the queue
    */
  val crossLaneWriteQueue: Seq[QueueIO[CrossWritePipe]] = Seq.tabulate(5)(i =>
    Queue.io(
      new CrossWritePipe(parameter),
      parameter.crossLaneVRFWriteEscapeQueueSize,
      pipe = true
    )
  )

  val crossWriteDeqRequest: DecoupledIO[LaneStage3Enqueue] = Wire(chiselTypeOf(dequeue))

  val queueDeqValid: Seq[Bool] = Seq.tabulate(4) { portIndex =>
    val queue: QueueIO[CrossWritePipe] = crossLaneWriteQueue(portIndex)
    val indexGrowth = Wire(chiselTypeOf(maskPipeReqReg.groupCounter))
    val maskSelect  = Wire(chiselTypeOf(queue.enq.bits.mask))
    val enqRequest  = if (portIndex < 2) {
      queue.enq.valid                     := crossWritePort2Enq(portIndex).valid || crossWritePort4Enq(portIndex).valid
      assert(!(crossWritePort2Enq(portIndex).valid && crossWritePort4Enq(portIndex).valid))
      crossWritePort2Enq(portIndex).ready := queue.enq.ready
      crossWritePort4Enq(portIndex).ready := queue.enq.ready
      indexGrowth                         := Mux(
        crossWritePort2Enq(portIndex).valid,
        changeUIntSize(crossWritePort2Enq(portIndex).bits.counter ## portIndex.U(1.W), indexGrowth.getWidth),
        changeUIntSize(crossWritePort4Enq(portIndex).bits.counter ## portIndex.U(2.W), indexGrowth.getWidth)
      )
      maskSelect                          := Mux(
        crossWritePort2Enq(portIndex).valid,
        FillInterleaved(2, crossWritePort2Enq(portIndex).bits.mask),
        FillInterleaved(4, crossWritePort4Enq(portIndex).bits.mask)
      )
      Mux(
        crossWritePort2Enq(portIndex).valid,
        crossWritePort2Enq(portIndex).bits,
        crossWritePort4Enq(portIndex).bits
      )
    } else {
      queue.enq.valid                     := crossWritePort4Enq(portIndex).valid
      crossWritePort4Enq(portIndex).ready := queue.enq.ready
      indexGrowth                         := changeUIntSize(
        crossWritePort4Enq(portIndex).bits.counter ## portIndex.U(2.W),
        indexGrowth.getWidth
      )
      maskSelect                          := FillInterleaved(4, crossWritePort4Enq(portIndex).bits.mask)
      crossWritePort4Enq(portIndex).bits
    }
    queue.enq.bits.data := enqRequest.data
    queue.enq.bits.mask         := maskSelect
    queue.enq.bits.groupCounter := indexGrowth
    assert(
      !queue.enq.fire || enqRequest.instructionIndex === maskPipeReqReg.instructionIndex,
      "Only one mask instruction can be executed at a time"
    )

    val groupMatch = queue.deq.bits.groupCounter === rxGroupIndex
    queue.deq.ready := crossWriteDeqRequest.ready && groupMatch
    queue.deq.valid && groupMatch
  }

  crossLaneWriteQueue.last.enq.valid             := freeCrossDataEnq.valid
  crossLaneWriteQueue.last.enq.bits.data         := freeCrossDataEnq.bits.data
  crossLaneWriteQueue.last.enq.bits.groupCounter := freeCrossDataEnq.bits.counter
  crossLaneWriteQueue.last.enq.bits.mask         := freeCrossDataEnq.bits.mask
  freeCrossDataEnq.ready                         := crossLaneWriteQueue.last.enq.ready
  crossLaneWriteQueue.last.deq.ready             := crossWriteDeqRequest.ready

  crossWriteDeqRequest.valid := VecInit(queueDeqValid).asUInt
  val deqRequestSelect: CrossWritePipe =
    Mux1H(queueDeqValid :+ crossLaneWriteQueue.last.deq.valid, crossLaneWriteQueue.map(_.deq.bits))
  crossWriteDeqRequest.bits                  := DontCare
  crossWriteDeqRequest.bits.data             := deqRequestSelect.data
  crossWriteDeqRequest.bits.mask             := deqRequestSelect.mask
  crossWriteDeqRequest.bits.groupCounter     := deqRequestSelect.groupCounter
  crossWriteDeqRequest.bits.instructionIndex := maskPipeReqReg.instructionIndex
  crossWriteDeqRequest.bits.vd               := maskPipeReqReg.vd
  when(crossWriteDeqRequest.fire) {
    rxGroupIndex := rxGroupIndex + 1.U
  }

  dequeue.valid              := (enqueue.valid && enqSendToDeq) || crossWriteDeqRequest.valid
  dequeue.bits               := Mux(crossWriteDeqRequest.valid, crossWriteDeqRequest.bits, enqueue.bits)
  enqueue.ready              := Mux(enqSendToDeq, dequeue.ready && !crossWriteDeqRequest.valid, maskReq.ready) || enqSendMaskPipe
  crossWriteDeqRequest.ready := dequeue.ready

  // for other
  val executeSize:    Int  = parameter.datapathWidth / 8
  val executeSizeBit: Int  = log2Ceil(executeSize)
  val executeIndex:   UInt = RegInit(0.U(executeSizeBit.W))

  val executeStageValid: Bool = RegInit(false.B)
  // update execute index
  val firstIndex:        UInt = OHToUInt(ffo(maskPipeEnqReq.mask))

  // current one hot depends on execute index
  val currentOHForExecuteGroup: UInt = UIntToOH(executeIndex)
  // Remaining to be requested
  val remainder:                UInt = maskPipeReqReg.mask & (~scanRightOr(currentOHForExecuteGroup)).asUInt
  // Finds the first unfiltered execution.
  val nextIndex:                UInt = OHToUInt(ffo(remainder))

  val slideRequest0:         DecoupledIO[SlideRequest0] = Wire(Decoupled(new SlideRequest0(parameter)))
  val slideRequestReg0:      ValidIO[SlideRequest0]     = RegInit(0.U.asTypeOf(Valid(new SlideRequest0(parameter))))
  val slideRequestDeqReady0: Bool                       = Wire(Bool())
  slideRequest0.valid := remainder.orR && executeStageValid && maskPipeEnqIsSlid
  slideRequest0.ready := !slideRequestReg0.valid || slideRequestDeqReady0

  val gatherRequest0:         DecoupledIO[GatherRequest0] = Wire(Decoupled(new GatherRequest0(parameter)))
  val gatherReg0:             ValidIO[GatherRequest0]     = RegInit(0.U.asTypeOf(Valid(new GatherRequest0(parameter))))
  val gatherRequestDeqReady0: Bool                        = Wire(Bool())
  gatherRequest0.valid := remainder.orR && executeStageValid && maskPipeEnqIsGather
  gatherRequest0.ready := !gatherReg0.valid || gatherRequestDeqReady0

  val executeStageDeqFire: Bool = Mux1H(
    Seq(
      maskPipeEnqIsSlid   -> slideRequest0.fire,
      maskPipeEnqIsGather -> gatherRequest0.fire
    )
  )
  when(slideRequest0.fire) {
    slideRequestReg0.bits := slideRequest0.bits
  }
  when(slideRequest0.fire ^ (slideRequestDeqReady0 && slideRequestReg0.valid)) {
    slideRequestReg0.valid := slideRequest0.fire
  }
  when(maskPipeEnqFire || executeStageDeqFire) {
    executeIndex := Mux(maskPipeEnqFire, firstIndex, nextIndex)
  }
  when(maskPipeEnqFire || !remainder.orR) {
    executeStageValid := maskPipeEnqFire && maskPipeEnqIsSlid
  }

  // todo: first gather16
  val sewSelect:            UInt = Mux(gather16, 2.U(3.W), maskPipeMessageReg.sew1H(2, 0))
  val byteMaskForExecution: UInt = Mux1H(
    sewSelect,
    Seq(
      currentOHForExecuteGroup,
      FillInterleaved(2, cutUIntBySize(currentOHForExecuteGroup, 2).head),
      FillInterleaved(4, cutUIntBySize(currentOHForExecuteGroup, 4).head)
    )
  )

  val bitMaskForExecution:                               UInt = FillInterleaved(8, byteMaskForExecution)
  def CollapseOperand(data: UInt, sign: Bool = false.B): UInt = {
    val dataMasked: UInt = data & bitMaskForExecution
    val dw        = data.getWidth - (data.getWidth % 32)
    // when sew = 0
    val collapse0 = Seq.tabulate(dw / 8)(i => dataMasked(8 * i + 7, 8 * i)).reduce(_ | _)
    // when sew = 1
    val collapse1 = Seq.tabulate(dw / 16)(i => dataMasked(16 * i + 15, 16 * i)).reduce(_ | _)
    val collapse2 = Seq.tabulate(dw / 32)(i => dataMasked(32 * i + 31, 32 * i)).reduce(_ | _)
    Mux1H(
      sewSelect,
      Seq(
        Fill(25, sign && collapse0(7)) ## collapse0,
        Fill(17, sign && collapse1(15)) ## collapse1,
        (sign && collapse2(31)) ## collapse2
      )
    )
  }

  val elementIndex: UInt = Mux1H(
    sewSelect,
    Seq(
      maskPipeReqReg.groupCounter ## laneIndex ## executeIndex,
      maskPipeReqReg.groupCounter ## laneIndex ## executeIndex(log2Ceil(parameter.dataPathByteWidth) - 2, 0),
      if (log2Ceil(parameter.dataPathByteWidth) > 2)
        maskPipeReqReg.groupCounter ## laneIndex ## executeIndex(log2Ceil(parameter.dataPathByteWidth) - 3, 0)
      else
        maskPipeReqReg.groupCounter ## laneIndex
    )
  )
  val source2:      UInt = CollapseOperand(maskPipeMessageReg.source2)
  val source1:      UInt = CollapseOperand(maskPipeMessageReg.source1)

  val sub:              Bool = !maskPipeReqReg.decodeResult(Decoder.maskPipeUop)(1)
  val source1IsScala:   Bool = maskPipeReqReg.decodeResult(Decoder.maskPipeUop)(0)
  val source1Select:    UInt = Mux(source1IsScala, maskPipeMessageReg.readFromScala, 1.U)
  val baseSelect:       UInt = elementIndex
  val source1Direction: UInt = Mux(sub, (~source1Select).asUInt, source1Select)

  val slideUp: Bool = maskPipeEnqReq.decodeResult(Decoder.maskPipeUop) === BitPat("b0011?")
  val slide1:  Bool = maskPipeEnqReq.decodeResult(Decoder.maskPipeUop) === BitPat("b001?0")

  slideRequest0.bits.address := baseSelect + source1Direction + sub
  slideRequest0.bits.data    := source2

  gatherRequest0.bits.executeIndex := elementIndex
  gatherRequest0.bits.readIndex    := source1

  val lagerThanVL: Bool = (source1Select >> parameter.vlMaxBits).asUInt.orR

  def indexAnalysis(sewInt: Int)(elementIndex: UInt, vlmul: UInt): Seq[UInt] = {
    val intLMULInput: UInt = (1.U << vlmul(1, 0)).asUInt
    val positionSize    = parameter.vlMaxBits - 1
    val allDataPosition = (elementIndex << sewInt).asUInt
    val dataPosition    = changeUIntSize(allDataPosition, positionSize)

    val dataPathBaseBits = log2Ceil(parameter.datapathWidth / 8)
    val dataOffset: UInt = dataPosition(dataPathBaseBits - 1, 0)
    val accessLane =
      if (parameter.laneNumber > 1)
        dataPosition(log2Ceil(parameter.laneNumber) + dataPathBaseBits - 1, dataPathBaseBits)
      else 0.U(1.W)
    // 32 bit / group
    val dataGroup  = (dataPosition >> (log2Ceil(parameter.laneNumber) + dataPathBaseBits)).asUInt
    val offsetWidth: Int = parameter.vrfParam.vrfOffsetBits
    val offset            = dataGroup(offsetWidth - 1, 0)
    val accessRegGrowth   = (dataGroup >> offsetWidth).asUInt
    val decimalProportion = offset ## accessLane
    // 1/8 register
    val decimal           = decimalProportion(decimalProportion.getWidth - 1, 0.max(decimalProportion.getWidth - 3))

    /** elementIndex needs to be compared with vlMax(vLen * lmul /sew) This calculation is too complicated We can change
      * the angle. Calculate the increment of the read register and compare it with lmul to know whether the index
      * exceeds vlMax. vlmul needs to distinguish between integers and floating points
      */
    val overlap  =
      (vlmul(2) && decimal >= intLMULInput(3, 1)) ||
        (!vlmul(2) && accessRegGrowth >= intLMULInput) ||
        (allDataPosition >> log2Ceil(parameter.vLen)).asUInt.orR
    val unChange = slideUp && (elementIndex.asBools.last || lagerThanVL)
    val elementValid: Bool = !unChange
    val notNeedRead:  Bool = overlap || !elementValid || lagerThanVL || unChange
    val reallyGrowth: UInt = changeUIntSize(accessRegGrowth, 3)
    Seq(dataOffset, accessLane, offset, reallyGrowth, notNeedRead, elementValid)
  }

  val analysisInput: UInt           = Mux(maskPipeEnqIsSlid, slideRequest0.bits.address, gatherReg0.bits.readIndex)
  val checkResult:   Seq[Seq[UInt]] = Seq(0, 1, 2).map { sewInt =>
    indexAnalysis(sewInt)(analysisInput, maskPipeMessageReg.vlmul)
  }

  val dataOffset   = Mux1H(maskPipeMessageReg.sew1H, checkResult.map(_.head))
  val accessLane   = Mux1H(maskPipeMessageReg.sew1H, checkResult.map(_(1)))
  val offset       = Mux1H(maskPipeMessageReg.sew1H, checkResult.map(_(2)))
  val reallyGrowth = Mux1H(maskPipeMessageReg.sew1H, checkResult.map(_(3)))
  val notNeedRead  = Mux1H(maskPipeMessageReg.sew1H, checkResult.map(_(4)))
  val elementValid = Mux1H(maskPipeMessageReg.sew1H, checkResult.map(_(5)))(0)

  val mask = Mux1H(maskPipeMessageReg.sew1H, Seq(1.U, 3.U, 15.U))

  val slideRequest1:    SlideRequest1          = Wire(new SlideRequest1(parameter))
  val slideRequestReg1: ValidIO[SlideRequest1] = RegInit(0.U.asTypeOf(Valid(new SlideRequest1(parameter))))
  slideRequestDeqReady0 := !slideRequestReg1.valid || freeCrossDataDeq.ready || !elementValid

  val slideRequest1EnqValid = slideRequestReg0.valid && elementValid
  val slideRequest1EnqFire: Bool = slideRequest1EnqValid && slideRequestDeqReady0
  when(slideRequest1EnqFire) {
    slideRequestReg1.bits := slideRequest1
  }
  when(slideRequest1EnqFire ^ freeCrossDataDeq.fire) {
    slideRequestReg1.valid := slideRequest1EnqFire
  }

  val replaceWithVs1: Bool = slide1 && Mux(
    maskPipeEnqReq.decodeResult(Decoder.maskPipeUop)(1),
    slideRequest0.bits.address === 0.U,
    slideRequest0.bits.address === (maskPipeMessageReg.vl - 1.U)
  )

  val dataSelect: UInt = Mux(
    replaceWithVs1,
    maskPipeMessageReg.readFromScala,
    Mux(
      notNeedRead.asBool,
      0.U,
      slideRequest0.bits.data
    )
  )

  slideRequest1.data         := dataSelect << (dataOffset ## 0.U(3.W))
  slideRequest1.mask         := mask << dataOffset
  slideRequest1.sink         := accessLane
  slideRequest1.groupCounter := reallyGrowth ## offset

  freeCrossDataDeq.valid        := slideRequestReg1.valid
  freeCrossDataDeq.bits.data    := slideRequestReg1.bits.data
  freeCrossDataDeq.bits.mask    := slideRequestReg1.bits.mask
  freeCrossDataDeq.bits.counter := slideRequestReg1.bits.groupCounter
  freeCrossDataDeq.bits.sink    := slideRequestReg1.bits.sink

  val gatherRequest1: GatherRequest1 = Wire(
    new GatherRequest1(
      parameter.datapathWidth,
      parameter.groupNumberBits,
      parameter.laneNumberBits,
      parameter.eLen
    )
  )

  val gatherRequestReg1: ValidIO[GatherRequest1] = RegInit(
    0.U.asTypeOf(
      Valid(
        new GatherRequest1(
          parameter.datapathWidth,
          parameter.groupNumberBits,
          parameter.laneNumberBits,
          parameter.eLen
        )
      )
    )
  )

  val gatherRequest1EnqValid = slideRequestReg0.valid
  val gatherRequest1EnqFire: Bool = gatherRequest1EnqValid && gatherRequestDeqReady0
  val gatherRequest1DeqFire: Bool = Wire(Bool())
  when(gatherRequest1EnqFire) {
    gatherRequestReg1.bits := gatherRequest1
  }
  when(gatherRequest1DeqFire ^ gatherRequest1EnqFire) {
    gatherRequestReg1.valid := gatherRequest1EnqFire
  }

  val sew:       UInt = OHToUInt(maskPipeMessageReg.sew1H)
  val writeMask: UInt =
    maskPipeMessageReg.sew1H(2) ## maskPipeMessageReg.sew1H(2) ## !maskPipeMessageReg.sew1H(0) ## true.B
  val writeByte: UInt = (gatherReg0.bits.executeIndex << sew).asUInt
  gatherRequest1.writeSink    := writeByte(
    parameter.dataPathByteBits + parameter.laneNumberBits - 1,
    parameter.dataPathByteBits
  )
  gatherRequest1.writeCounter := writeByte >> (parameter.dataPathByteBits + parameter.laneNumberBits)
  gatherRequest1.writeOffset  := writeByte(parameter.dataPathByteBits - 1, 0)
  gatherRequest1.mask         := writeByte(parameter.dataPathByteBits - 1, 0)

  gatherRequest1.readSink    := accessLane
  gatherRequest1.readCounter := reallyGrowth ## offset
  gatherRequest1.readOffset  := dataOffset
  gatherRequest1.skipRead    := notNeedRead

  // vrgather.vv vd, vs2, vs1, vm # vd[i] = (vs1[i] >= VLMAX) ? 0 : vs2[vs1[i]];
  // skipRead: vs1[i] >= VLMAX
  // gatherRequest1 deq
  // needRead =>  freeCrossRequest
  // !needRead => same lane => dequeue
  // !needRead => !same lane => freeCrossData

  val sameLane: Bool = gatherRequestReg1.bits.writeSink === laneIndex

  freeCrossReqDeq.valid             := gatherRequestReg1.valid && !gatherRequestReg1.bits.skipRead
  freeCrossReqDeq.bits.readSink     := gatherRequestReg1.bits.readSink
  freeCrossReqDeq.bits.readCounter  := gatherRequestReg1.bits.readCounter
  freeCrossReqDeq.bits.readOffset   := gatherRequestReg1.bits.readOffset
  freeCrossReqDeq.bits.writeSink    := gatherRequestReg1.bits.writeSink
  freeCrossReqDeq.bits.writeCounter := gatherRequestReg1.bits.writeCounter
  freeCrossReqDeq.bits.writeOffset  := gatherRequestReg1.bits.writeOffset

  when(maskPipeEnqIsGather) {
    freeCrossDataDeq.valid        := gatherRequestReg1.valid && gatherRequestReg1.bits.skipRead && !sameLane
    freeCrossDataDeq.bits.data    := 0.U
    freeCrossDataDeq.bits.mask    := gatherRequestReg1.bits.mask
    freeCrossDataDeq.bits.counter := gatherRequestReg1.bits.writeCounter
    freeCrossDataDeq.bits.sink    := gatherRequestReg1.bits.writeSink

    crossLaneWriteQueue.last.enq.valid             := gatherRequestReg1.valid && gatherRequestReg1.bits.skipRead && sameLane
    crossLaneWriteQueue.last.enq.bits.data         := 0.U
    crossLaneWriteQueue.last.enq.bits.mask         := gatherRequestReg1.bits.mask
    crossLaneWriteQueue.last.enq.bits.groupCounter := gatherRequestReg1.bits.writeCounter
  }

  val gatherRequest1DeqReady: Bool = Mux(
    gatherRequestReg1.bits.skipRead,
    Mux(
      sameLane,
      crossLaneWriteQueue.last.enq.ready,
      freeCrossDataDeq.ready
    ),
    freeCrossDataDeq.ready
  )
  gatherRequest1DeqFire := gatherRequestReg1.valid && gatherRequest1DeqReady

  gatherRequestDeqReady0 := !gatherRequestReg1.valid || gatherRequest1DeqReady

  // todo: The second pipe for gather
  freeCrossReqEnq.ready := true.B
  // enq ready
  val extendType:    Bool = maskPipeEnqIsExtend
  val crossDataType: Bool = maskPipeEnqIsGather || maskPipeEnqIsSlid
  maskPipeDeqReady := Mux1H(
    Seq(
      extendType    -> crossWriteState.andR,
      crossDataType -> !remainder.orR
    )
  )
}
