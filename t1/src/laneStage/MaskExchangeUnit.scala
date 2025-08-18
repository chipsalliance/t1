// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lane

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._
import chisel3.util.experimental.decode.DecodeBundle
import org.chipsalliance.dwbb.stdlib.queue
import org.chipsalliance.dwbb.stdlib.queue.{Queue, QueueIO}
import org.chipsalliance.t1.rtl._
import org.chipsalliance.t1.rtl.decoder.Decoder

class PipeForMaskUnit(parameter: LaneParameter) extends Bundle {
  val sew1H:         UInt         = UInt(3.W)
  val source1:       UInt         = UInt(parameter.datapathWidth.W)
  val source2:       UInt         = UInt(parameter.datapathWidth.W)
  val readFromScala: UInt         = UInt(parameter.eLen.W)
  val vl:            UInt         = UInt(parameter.vlMaxBits.W)
  val vlmul:         UInt         = UInt(3.W)
  val csr:           CSRInterface = new CSRInterface(parameter.vlMaxBits)
}

class MaskExchangeRelease extends Bundle {
  val maskPipe:   Bool = Bool()
  val secondPipe: Bool = Bool()
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
  val readAddress = UInt(parameter.eLen.W)
  val data: UInt = UInt(parameter.eLen.W)
}

class SlideRequest1(parameter: LaneParameter) extends Bundle {
  val sink:         UInt = UInt(parameter.laneNumberBits.W)
  val data:         UInt = UInt(parameter.datapathWidth.W)
  val mask:         UInt = UInt((parameter.datapathWidth / 8).W)
  val groupCounter: UInt = UInt(parameter.groupNumberBits.W)
}

class GatherRequest0(parameter: LaneParameter) extends Bundle {
  val executeIndex: UInt = UInt(parameter.vlMaxBits.W)
  val readIndex:    UInt = UInt(parameter.eLen.W)
}

class PipeForSecondPipe(datapathWidth: Int, groupNumberBits: Int, laneNumberBits: Int, eLen: Int) extends Bundle {
  val readOffset:   UInt = UInt(log2Ceil(datapathWidth / 8).W)
  val writeSink:    UInt = UInt(laneNumberBits.W)
  val writeCounter: UInt = UInt(groupNumberBits.W)
  val writeOffset:  UInt = UInt(log2Ceil(datapathWidth / 8).W)
}

class GatherRequest1(datapathWidth: Int, groupNumberBits: Int, laneNumberBits: Int, eLen: Int) extends Bundle {
  val readSink:    UInt = UInt(laneNumberBits.W)
  val readCounter: UInt = UInt(groupNumberBits.W)
  val skipRead:    Bool = Bool()

  val readOffset:   UInt = UInt(log2Ceil(datapathWidth / 8).W)
  val writeSink:    UInt = UInt(laneNumberBits.W)
  val writeCounter: UInt = UInt(groupNumberBits.W)
  val writeOffset:  UInt = UInt(log2Ceil(datapathWidth / 8).W)
  val mask:         UInt = UInt((datapathWidth / 8).W)
}

class reduceMaskRequest(datapathWidth: Int) extends Bundle {
  val data:    UInt = UInt(datapathWidth.W)
  val hitLast: Bool = Bool()
  val finish = Bool()
}

class MaskStageToken(parameter: LaneParameter) extends Bundle {
  val freeCrossWrite:          ValidIO[UInt] = Valid(UInt(parameter.instructionIndexBits.W))
  val maskStageRequestRelease: ValidIO[UInt] = Valid(UInt(parameter.instructionIndexBits.W))
  val maskStageClear:          Bool          = Output(Bool())
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
          parameter.laneNumberBits,
          parameter.instructionIndexBits
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
            parameter.laneNumberBits,
            parameter.instructionIndexBits
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
  val maskPipeRelease: MaskExchangeRelease = IO(Output(new MaskExchangeRelease))

  @public
  val laneIndex: UInt = IO(Input(UInt(parameter.laneNumberBits.W)))

  @public
  val reduceVRFRequest: DecoupledIO[SlotRequestToVFU] = IO(Decoupled(new SlotRequestToVFU(parameter)))

  @public
  val reduceRequestDecode: DecodeBundle = IO(Output(Decoder.bundle(parameter.decoderParam)))

  @public
  val reduceResponse: ValidIO[VFUResponseToSlot] = IO(Flipped(Valid(new VFUResponseToSlot(parameter))))

  @public
  val reduceMaskRequest: DecoupledIO[reduceMaskRequest] = IO(Decoupled(new reduceMaskRequest(parameter.datapathWidth)))

  @public
  val reduceMaskResponse: DecoupledIO[reduceMaskRequest] = IO(
    Flipped(Decoupled(new reduceMaskRequest(parameter.datapathWidth)))
  )

  @public
  val token: MaskStageToken = IO(new MaskStageToken(parameter))

  @public
  val instructionValid: UInt = IO(Input(UInt(parameter.chaining1HBits.W)))

  // todo: sSendResponse -> sendResponse
  val enqIsMaskRequest: Bool = !enqueue.bits.sSendResponse && !enqueue.bits.decodeResult(Decoder.maskPipeType)
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
  maskReqQueue.enq.valid         := enqueue.valid && enqSendMaskPipe && !enqueue.bits.secondPipe.get
  maskPipeRelease.maskPipe       := maskReqQueue.deq.fire
  maskReqQueue.enq.bits.req      := enqueue.bits
  maskReqQueue.enq.bits.maskPipe := pipeForMask

  // second pipe request queue
  val secondReqQueue: queue.QueueIO[MaskPipeBundle] =
    Queue.io(new MaskPipeBundle(parameter), parameter.maskRequestQueueSize)
  secondReqQueue.enq.valid         := enqueue.valid && enqSendMaskPipe && enqueue.bits.secondPipe.get
  maskPipeRelease.secondPipe       := secondReqQueue.deq.fire
  secondReqQueue.enq.bits.req      := enqueue.bits
  secondReqQueue.enq.bits.maskPipe := pipeForMask

  // opcode (0, 1) cross write 2/4
  val crossWriteState: UInt = RegInit(15.U(4.W))

  // todo: other type
  val maskPipeDeqReady:   Bool              = Wire(Bool())
  val maskPipeEnqReq:     LaneStage3Enqueue = maskReqQueue.deq.bits.req
  val maskPipeReqReg:     LaneStage3Enqueue = RegInit(0.U.asTypeOf(maskPipeEnqReq))
  val maskPipeMessageReg: PipeForMaskUnit   = RegInit(0.U.asTypeOf(new PipeForMaskUnit(parameter)))
  val rxGroupIndex:       UInt              = RegInit(0.U(parameter.groupNumberBits.W))
  val slide0Replenish = RegInit(false.B)

  // reduce state machine
  // If there is no lane expansion or fd, only the first lane will have fold & sWrite,
  // but to make all lanes look the same, everyone keeps it
  val idle :: sRequest :: wResponse :: fold :: sMaskRequest :: wMaskRequest :: sWrite :: orderFold :: orderWaitResponse :: waitNewGroup :: waitLastFoldResponse :: Nil =
    Enum(11)
  val reduceState:  UInt = RegInit(idle)
  val reduceResult: UInt = RegInit(0.U(parameter.datapathWidth.W))
  val hitLast = RegInit(false.B)
  val finish  = RegInit(false.B)
  val reduceResultSize: UInt = RegInit(0.U(3.W))

  val stateIdle:         Bool = reduceState === idle         // 0
  val stateSRequest:     Bool = reduceState === sRequest     // 1
  val stateWResponse:    Bool = reduceState === wResponse    // 2
  val stateFold:         Bool = reduceState === fold         // 3
  val stateSMaskRequest: Bool = reduceState === sMaskRequest // 4
  val stateWMaskRequest: Bool = reduceState === wMaskRequest // 5
  val stateSWrite:       Bool = reduceState === sWrite       // 6

  // for order reduce
  val stateOrderFold: Bool = reduceState === orderFold            // 7
  val stateOrderWait: Bool = reduceState === orderWaitResponse    // 8
  val stateWaitNew:   Bool = reduceState === waitNewGroup         // 9
  val stateWLFR:      Bool = reduceState === waitLastFoldResponse // 10

  val firstLane = laneIndex === 0.U
  val foldFinish: Bool = Wire(Bool())

  val maskPipeValid:   Bool      = RegInit(false.B)
  val crossWriteFire2: Vec[Bool] = Wire(Vec(2, Bool()))
  val crossWriteFire4: Vec[Bool] = Wire(Vec(4, Bool()))
  val crossWriteDeqFire = crossWriteFire4.asUInt | crossWriteFire2.asUInt

  val enqIsGather16: Bool = maskPipeEnqReq.decodeResult(Decoder.maskPipeUop) === BitPat("b00011")
  val enqSewSelect = Mux(enqIsGather16, 2.U(3.W), maskReqQueue.deq.bits.maskPipe.sew1H(2, 0))
  // todo
  val enqMask: UInt = Mux1H(
    enqSewSelect,
    Seq(
      maskPipeEnqReq.mask,
      VecInit(cutUInt(maskPipeEnqReq.mask, 2).map(_.orR)).asUInt,
      VecInit(cutUInt(maskPipeEnqReq.mask, 4).map(_.orR)).asUInt
    )
  )

  val maskPipeEnqIsExtend: Bool = maskPipeEnqReq.decodeResult(Decoder.maskPipeUop) === BitPat("b0000?")
  val maskPipeEnqReduce:   Bool = maskPipeEnqReq.decodeResult(Decoder.maskPipeUop) === BitPat("b010??")
  val maskPipeEnqOrder:    Bool = maskPipeEnqReq.decodeResult(Decoder.maskPipeUop) === BitPat("b01011")
  val enqSlide1Up:         Bool = maskPipeEnqReq.decodeResult(Decoder.maskPipeUop) === BitPat("b00110")
  val enqIsSlide:          Bool = maskPipeEnqReq.decodeResult(Decoder.maskPipeUop) === BitPat("b001??")
  val enqIsGather:         Bool = maskPipeEnqReq.decodeResult(Decoder.maskPipeUop) === BitPat("b0001?")

  val maskPipeIsExtend:   Bool = maskPipeReqReg.decodeResult(Decoder.maskPipeUop) === BitPat("b0000?")
  val maskPipeIsGather:   Bool = maskPipeReqReg.decodeResult(Decoder.maskPipeUop) === BitPat("b0001?")
  val maskPipeIsReduce:   Bool = maskPipeReqReg.decodeResult(Decoder.maskPipeUop) === BitPat("b010??")
  val maskPipeIsGather16: Bool = maskPipeReqReg.decodeResult(Decoder.maskPipeUop) === BitPat("b00011")
  val maskPipeIsSlid:     Bool = maskPipeReqReg.decodeResult(Decoder.maskPipeUop) === BitPat("b001??")
  val maskPipeIsPop:      Bool = maskPipeReqReg.decodeResult(Decoder.popCount)

  val maskPipeIsOrder: Bool = maskPipeReqReg.decodeResult(Decoder.maskPipeUop) === BitPat("b01011")

  val maskPipeDeqFire            = maskPipeValid && maskPipeDeqReady
  val maskPipeEnqFire            = maskReqQueue.deq.fire
  val enqValidElementExecuteType = !(enqIsSlide || enqIsGather) || maskPipeEnqReq.mask.orR
  val normalEnqFire              = maskPipeEnqFire && !maskReqQueue.deq.bits.req.emptyPipe.get
  val validEnqFire               = normalEnqFire && enqValidElementExecuteType
  when(maskPipeDeqFire ^ validEnqFire) {
    maskPipeValid := validEnqFire
  }

  val enqSew: UInt =
    (maskReqQueue.deq.bits.maskPipe.sew1H << maskPipeEnqReq.decodeResult(Decoder.widenReduce)).asUInt(2, 0)
  val enqByteMask = enqSew(2) ## enqSew(2) ## !enqSew(0) ## true.B
  val enqBitMask: UInt = FillInterleaved(8, enqByteMask)

  val maskStageNoConflict: Bool = Wire(Bool())
  maskReqQueue.deq.ready := (!maskPipeValid || maskPipeDeqReady) && maskStageNoConflict
  val opcode1H: UInt = UIntToOH(maskPipeReqReg.decodeResult(Decoder.maskPipeUop))
  // todo
  val firstFroup = maskPipeEnqReq.groupCounter === 0.U
  // update register
  when(maskPipeEnqFire) {
    maskPipeReqReg := maskPipeEnqReq
    when(maskPipeEnqReq.instructionIndex =/= maskPipeReqReg.instructionIndex) {
      rxGroupIndex := 0.U
    }
  }
  val orderHitLast: Bool = (maskPipeEnqReq.groupCounter ## laneIndex) ===
    ((maskReqQueue.deq.bits.maskPipe.vl - 1.U) >> log2Ceil(parameter.laneScale)).asUInt
  val reduceStart: Bool = Mux(maskPipeEnqOrder, firstFroup, !maskPipeEnqReq.sSendResponse)
  when(normalEnqFire) {
    maskPipeReqReg.mask := enqMask
    maskPipeMessageReg  := maskReqQueue.deq.bits.maskPipe
    hitLast             := orderHitLast || (maskPipeEnqReduce && !maskPipeEnqOrder)
    when(firstFroup) {
      finish := false.B
    }
    reduceResultSize    := log2Ceil(parameter.datapathWidth / 8).U
    when(maskPipeEnqReduce) {
      when(firstFroup && firstLane) {
        reduceResult := maskReqQueue.deq.bits.maskPipe.source1 & enqBitMask
      }
      when(reduceStart) {
        reduceState := Mux(firstLane, sRequest, wMaskRequest)
      }
    }
    when(maskPipeEnqIsExtend) {
      crossWriteState := Mux(maskPipeEnqReq.decodeResult(Decoder.maskPipeUop)(0), 0.U, 12.U)
    }
    when(enqSlide1Up && firstFroup && firstLane && maskPipeEnqReq.maskE0) {
      slide0Replenish := true.B
    }
  }
  when(crossWriteDeqFire.orR) {
    crossWriteState := crossWriteState | crossWriteDeqFire
  }

  val extendData2:     UInt      = Mux(
    maskPipeMessageReg.sew1H(1),
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
  val sewForCross2 = (maskPipeMessageReg.sew1H << 1)(2, 0)
  val maskExtend2  = Mux1H(
    sewForCross2,
    Seq(1, 2, 4).map(s => FillInterleaved(s, maskPipeReqReg.mask))
  )
  // VRF cross write
  crossWritePort2Deq.zipWithIndex.foreach { case (port, index) =>
    port.valid                 := maskPipeValid && !crossWriteState(index) && opcode1H(0)
    port.bits.mask             := cutUInt(maskExtend2, parameter.datapathWidth / 8)(index)
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
  val sewForCross4 = (maskPipeMessageReg.sew1H << 2)(2, 0)
  val maskExtend4  = Mux1H(
    sewForCross4,
    Seq(1, 2, 4).map(s => FillInterleaved(s, maskPipeReqReg.mask))
  )
  // VRF cross write
  crossWritePort4Deq.zipWithIndex.foreach { case (port, index) =>
    port.valid                 := maskPipeValid && !crossWriteState(index) && opcode1H(1)
    port.bits.mask             := cutUInt(maskExtend4, parameter.datapathWidth / 8)(index)
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

  val freeWriteArbiter: Arbiter[CrossWritePipe] = Module(new Arbiter(new CrossWritePipe(parameter), 5))
  crossLaneWriteQueue.last.enq <> freeWriteArbiter.io.out

  val crossWriteDeqRequest: DecoupledIO[LaneStage3Enqueue] = Wire(chiselTypeOf(dequeue))

  val queueDeqValid: Seq[Bool] = Seq.tabulate(4) { portIndex =>
    val queue: QueueIO[CrossWritePipe] = crossLaneWriteQueue(portIndex)
    val indexGrowth = Wire(chiselTypeOf(maskPipeReqReg.groupCounter))
    val enqRequest  = if (portIndex < 2) {
      val indexMatch2 = crossWritePort2Enq(portIndex).bits.instructionIndex === maskPipeReqReg.instructionIndex
      val indexMatch4 = crossWritePort4Enq(portIndex).bits.instructionIndex === maskPipeReqReg.instructionIndex
      queue.enq.valid                     := (crossWritePort2Enq(portIndex).valid && indexMatch2) || (indexMatch4 && crossWritePort4Enq(
        portIndex
      ).valid)
      assert(!(crossWritePort2Enq(portIndex).valid && crossWritePort4Enq(portIndex).valid))
      crossWritePort2Enq(portIndex).ready := queue.enq.ready && indexMatch2
      crossWritePort4Enq(portIndex).ready := queue.enq.ready && indexMatch4
      indexGrowth                         := Mux(
        crossWritePort2Enq(portIndex).valid,
        changeUIntSize(crossWritePort2Enq(portIndex).bits.counter ## portIndex.U(1.W), indexGrowth.getWidth),
        changeUIntSize(crossWritePort4Enq(portIndex).bits.counter ## portIndex.U(2.W), indexGrowth.getWidth)
      )
      Mux(
        crossWritePort2Enq(portIndex).valid,
        crossWritePort2Enq(portIndex).bits,
        crossWritePort4Enq(portIndex).bits
      )
    } else {
      val indexMatch4 = crossWritePort4Enq(portIndex).bits.instructionIndex === maskPipeReqReg.instructionIndex
      queue.enq.valid                     := indexMatch4 && crossWritePort4Enq(portIndex).valid
      crossWritePort4Enq(portIndex).ready := indexMatch4 && queue.enq.ready
      indexGrowth                         := changeUIntSize(
        crossWritePort4Enq(portIndex).bits.counter ## portIndex.U(2.W),
        indexGrowth.getWidth
      )
      crossWritePort4Enq(portIndex).bits
    }
    queue.enq.bits.data := enqRequest.data
    queue.enq.bits.mask         := enqRequest.mask
    queue.enq.bits.groupCounter := indexGrowth

    val groupMatch = queue.deq.bits.groupCounter === rxGroupIndex
    queue.deq.ready := crossWriteDeqRequest.ready && groupMatch
    queue.deq.valid && groupMatch
  }

  val indexMatch: Bool = freeCrossDataEnq.bits.instructionIndex === maskPipeReqReg.instructionIndex
  freeWriteArbiter.io.in.head.valid             := freeCrossDataEnq.valid && indexMatch
  freeWriteArbiter.io.in.head.bits.data         := freeCrossDataEnq.bits.data
  freeWriteArbiter.io.in.head.bits.groupCounter := freeCrossDataEnq.bits.counter
  freeWriteArbiter.io.in.head.bits.mask         := freeCrossDataEnq.bits.mask
  freeCrossDataEnq.ready                        := freeWriteArbiter.io.in.head.ready && indexMatch
  crossLaneWriteQueue.last.deq.ready            := crossWriteDeqRequest.ready

  crossWriteDeqRequest.valid := VecInit(queueDeqValid :+ crossLaneWriteQueue.last.deq.valid).asUInt.orR
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

  val maskPipeReady: Bool =
    enqueue.bits.secondPipe.map(s => Mux(s, secondReqQueue.enq.ready, maskReqQueue.enq.ready)).getOrElse(true.B)
  dequeue.valid              := (enqueue.valid && enqSendToDeq) || crossWriteDeqRequest.valid
  dequeue.bits               := Mux(crossWriteDeqRequest.valid, crossWriteDeqRequest.bits, enqueue.bits)
  enqueue.ready              := Mux(
    enqSendToDeq,
    dequeue.ready && !crossWriteDeqRequest.valid,
    Mux(enqSendMaskPipe, maskPipeReady, maskReq.ready)
  )
  crossWriteDeqRequest.ready := dequeue.ready

  // for other
  val executeSize:    Int  = parameter.datapathWidth / 8
  val executeSizeBit: Int  = log2Ceil(executeSize)
  val executeIndex:   UInt = RegInit(0.U(executeSizeBit.W))

  // update execute index
  val firstIndex: UInt = OHToUInt(ffo(enqMask))

  // current one hot depends on execute index
  val currentOHForExecuteGroup: UInt = UIntToOH(executeIndex)
  // Remaining to be requested
  val remainder:                UInt = maskPipeReqReg.mask & (~scanRightOr(currentOHForExecuteGroup)).asUInt
  // Finds the first unfiltered execution.
  val nextIndex:                UInt = OHToUInt(ffo(remainder))

  val slideRequest0:         DecoupledIO[SlideRequest0] = Wire(Decoupled(new SlideRequest0(parameter)))
  val slideRequestReg0:      ValidIO[SlideRequest0]     = RegInit(0.U.asTypeOf(Valid(new SlideRequest0(parameter))))
  val slideRequestDeqReady0: Bool                       = Wire(Bool())
  slideRequest0.valid := maskPipeValid && maskPipeIsSlid
  slideRequest0.ready := !slideRequestReg0.valid || slideRequestDeqReady0

  val gatherRequest0:         DecoupledIO[GatherRequest0] = Wire(Decoupled(new GatherRequest0(parameter)))
  val gatherRequestReg0:      ValidIO[GatherRequest0]     = RegInit(0.U.asTypeOf(Valid(new GatherRequest0(parameter))))
  val gatherRequestDeqReady0: Bool                        = Wire(Bool())
  gatherRequest0.valid := maskPipeValid && maskPipeIsGather
  gatherRequest0.ready := !gatherRequestReg0.valid || gatherRequestDeqReady0

  val executeStageDeqFire: Bool = Mux1H(
    Seq(
      maskPipeIsSlid   -> slideRequest0.fire,
      maskPipeIsGather -> gatherRequest0.fire
    )
  )
  when(slideRequest0.fire) {
    slideRequestReg0.bits := slideRequest0.bits
  }
  when(slideRequest0.fire ^ (slideRequestDeqReady0 && slideRequestReg0.valid)) {
    slideRequestReg0.valid := slideRequest0.fire
  }
  when(gatherRequest0.fire) {
    gatherRequestReg0.bits := gatherRequest0.bits
  }
  when(gatherRequest0.fire ^ (gatherRequestDeqReady0 && gatherRequestReg0.valid)) {
    gatherRequestReg0.valid := gatherRequest0.fire
  }
  when(maskPipeEnqFire || executeStageDeqFire) {
    executeIndex := Mux(maskPipeEnqFire, firstIndex, nextIndex)
  }

  // todo: first gather16
  val sew1HSelect:          UInt = Mux(maskPipeIsGather16, 2.U(3.W), maskPipeMessageReg.sew1H(2, 0))
  val byteMaskForExecution: UInt = Mux1H(
    sew1HSelect,
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
      sew1HSelect,
      Seq(
        Fill(25, sign && collapse0(7)) ## collapse0,
        Fill(17, sign && collapse1(15)) ## collapse1,
        (sign && collapse2(31)) ## collapse2
      )
    )
  }

  val slideSizeNegative = !maskPipeReqReg.decodeResult(Decoder.itype) && maskPipeMessageReg.readFromScala(
    parameter.eLen - 1
  ) && maskPipeReqReg.groupCounter.andR
  val elementHead       = Fill(parameter.eLen, slideSizeNegative)
  val elementIndex: UInt = Mux1H(
    sew1HSelect,
    Seq(
      elementHead ## maskPipeReqReg.groupCounter ## laneIndex ## executeIndex,
      elementHead ## maskPipeReqReg.groupCounter ## laneIndex ## executeIndex(
        log2Ceil(parameter.dataPathByteWidth) - 2,
        0
      ),
      if (log2Ceil(parameter.dataPathByteWidth) > 2)
        elementHead ## maskPipeReqReg.groupCounter ## laneIndex ## executeIndex(
          log2Ceil(parameter.dataPathByteWidth) - 3,
          0
        )
      else
        elementHead ## maskPipeReqReg.groupCounter ## laneIndex
    )
  )
  val source2:      UInt = CollapseOperand(maskPipeMessageReg.source2)
  val source1:      UInt = CollapseOperand(maskPipeMessageReg.source1)

  val sub:              Bool = !maskPipeReqReg.decodeResult(Decoder.maskPipeUop)(1)
  val source1IsScala:   Bool = maskPipeReqReg.decodeResult(Decoder.maskPipeUop)(0)
  val source1Select:    UInt = Mux(
    source1IsScala,
    Mux(
      maskPipeReqReg.decodeResult(Decoder.itype),
      maskPipeMessageReg.readFromScala(4, 0),
      maskPipeMessageReg.readFromScala
    ),
    1.U
  )
  val lagerThanVL:      Bool = (source1Select >> parameter.vlMaxBits).asUInt.orR
  val baseSelect:       UInt = elementIndex
  val source1Direction: UInt = Mux(
    sub,
    Mux(lagerThanVL, 0.U, (~source1Select).asUInt),
    source1Select
  )

  val slideUp: Bool = maskPipeReqReg.decodeResult(Decoder.maskPipeUop) === BitPat("b0011?")
  val slide1:  Bool = maskPipeReqReg.decodeResult(Decoder.maskPipeUop) === BitPat("b001?0")

  slideRequest0.bits.address     := baseSelect + source1Direction + (sub && !lagerThanVL)
  slideRequest0.bits.readAddress := baseSelect
  slideRequest0.bits.data        := source2

  gatherRequest0.bits.executeIndex := elementIndex
  gatherRequest0.bits.readIndex    := source1

  def indexAnalysis(sewInt: Int)(elementIndex: UInt, vlmul: UInt, baseValid: Bool, readIndex: UInt): Seq[UInt] = {
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

    // for read
    val readAllDataPosition   = (readIndex << sewInt).asUInt
    val readDataPosition      = changeUIntSize(readAllDataPosition, positionSize)
    val readDataGroup         = (readDataPosition >> (log2Ceil(parameter.laneNumber) + dataPathBaseBits)).asUInt
    val readAccessLane        =
      if (parameter.laneNumber > 1)
        readDataPosition(log2Ceil(parameter.laneNumber) + dataPathBaseBits - 1, dataPathBaseBits)
      else 0.U(1.W)
    val readOffset            = readDataGroup(offsetWidth - 1, 0)
    val readAccessRegGrowth   = (readDataGroup >> offsetWidth).asUInt
    val readDecimalProportion = readOffset ## readAccessLane
    val readDecimal           = readDecimalProportion(decimalProportion.getWidth - 1, 0.max(decimalProportion.getWidth - 3))

    /** elementIndex needs to be compared with vlMax(vLen * lmul /sew) This calculation is too complicated We can change
      * the angle. Calculate the increment of the read register and compare it with lmul to know whether the index
      * exceeds vlMax. vlmul needs to distinguish between integers and floating points
      */
    val readOverlap =
      (vlmul(2) && readDecimal >= intLMULInput(3, 1)) ||
        (!vlmul(2) && readAccessRegGrowth >= intLMULInput) ||
        (readAllDataPosition >> log2Ceil(parameter.vLen)).asUInt.orR
    val unChange    = slideUp && (elementIndex.asBools.last || lagerThanVL)
    val elementValid: Bool = !unChange && !elementIndex(elementIndex.getWidth - 1) && baseValid
    val notNeedRead:  Bool = readOverlap || !elementValid || lagerThanVL || unChange
    val reallyGrowth: UInt = changeUIntSize(accessRegGrowth, 3)
    Seq(dataOffset, accessLane, offset, reallyGrowth, notNeedRead, elementValid)
  }

  val analysisReadAdd:  UInt = Mux(maskPipeIsSlid, slideRequestReg0.bits.readAddress, gatherRequestReg0.bits.readIndex)
  val analysisWriteAdd: UInt = Mux(maskPipeIsSlid, slideRequestReg0.bits.address, gatherRequestReg0.bits.readIndex)
  val analysisBaseValid = (analysisWriteAdd < maskPipeMessageReg.vl) || !maskPipeIsSlid
  val checkResult: Seq[Seq[UInt]] = Seq(0, 1, 2).map { sewInt =>
    indexAnalysis(sewInt)(analysisWriteAdd, maskPipeMessageReg.vlmul, analysisBaseValid, analysisReadAdd)
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
    maskPipeReqReg.decodeResult(Decoder.maskPipeUop)(1),
    slideRequestReg0.bits.address === 0.U,
    slideRequestReg0.bits.address === (maskPipeMessageReg.vl - 1.U)
  )

  val dataSelect: UInt = Mux(
    replaceWithVs1,
    maskPipeMessageReg.readFromScala,
    Mux(
      notNeedRead.asBool,
      0.U,
      slideRequestReg0.bits.data
    )
  )

  slideRequest1.data         := dataSelect << (dataOffset ## 0.U(3.W))
  slideRequest1.mask         := mask << dataOffset
  slideRequest1.sink         := accessLane
  slideRequest1.groupCounter := reallyGrowth ## offset

  freeWriteArbiter.io.in(1).valid             := slide0Replenish && !freeCrossDataEnq.valid
  freeWriteArbiter.io.in(1).bits.data         := maskPipeMessageReg.readFromScala
  freeWriteArbiter.io.in(1).bits.mask         := mask
  freeWriteArbiter.io.in(1).bits.groupCounter := 0.U
  when(freeWriteArbiter.io.in(1).fire) {
    slide0Replenish := false.B
  }

  freeCrossDataDeq.valid        := slideRequestReg1.valid
  freeCrossDataDeq.bits.data    := slideRequestReg1.bits.data
  freeCrossDataDeq.bits.mask    := slideRequestReg1.bits.mask
  freeCrossDataDeq.bits.counter := slideRequestReg1.bits.groupCounter
  freeCrossDataDeq.bits.sink    := slideRequestReg1.bits.sink

  freeCrossDataDeq.bits.instructionIndex := maskPipeReqReg.instructionIndex

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

  val gatherRequest1EnqValid = gatherRequestReg0.valid
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
  val writeByte: UInt = (gatherRequestReg0.bits.executeIndex << sew).asUInt
  gatherRequest1.writeSink    := writeByte(
    parameter.dataPathByteBits + parameter.laneNumberBits - 1,
    parameter.dataPathByteBits
  )
  gatherRequest1.writeCounter := writeByte >> (parameter.dataPathByteBits + parameter.laneNumberBits)
  gatherRequest1.writeOffset  := writeByte(parameter.dataPathByteBits - 1, 0)
  gatherRequest1.mask         := (writeMask << writeByte(parameter.dataPathByteBits - 1, 0)).asUInt

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

  when(maskPipeIsGather) {
    freeCrossDataDeq.valid        := gatherRequestReg1.valid && gatherRequestReg1.bits.skipRead && !sameLane
    freeCrossDataDeq.bits.data    := 0.U
    freeCrossDataDeq.bits.mask    := gatherRequestReg1.bits.mask
    freeCrossDataDeq.bits.counter := gatherRequestReg1.bits.writeCounter
    freeCrossDataDeq.bits.sink    := gatherRequestReg1.bits.writeSink
  }

  freeWriteArbiter.io.in(4).valid             := gatherRequestReg1.valid && gatherRequestReg1.bits.skipRead && sameLane
  freeWriteArbiter.io.in(4).bits.data         := 0.U
  freeWriteArbiter.io.in(4).bits.mask         := gatherRequestReg1.bits.mask
  freeWriteArbiter.io.in(4).bits.groupCounter := gatherRequestReg1.bits.writeCounter

  val freeCrossDataUsedBySecondPipe: Bool = Wire(Bool())
  val gatherRequest1DeqReady:        Bool = Mux(
    gatherRequestReg1.bits.skipRead,
    Mux(
      sameLane,
      freeWriteArbiter.io.in(4).ready,
      freeCrossDataDeq.ready && !freeCrossDataUsedBySecondPipe
    ),
    freeCrossReqDeq.ready
  )
  gatherRequest1DeqFire := gatherRequestReg1.valid && gatherRequest1DeqReady

  gatherRequestDeqReady0 := !gatherRequestReg1.valid || gatherRequest1DeqReady

  // enq ready
  val extendType:    Bool = maskPipeIsExtend
  val crossDataType: Bool = maskPipeIsGather || maskPipeIsSlid
  maskPipeDeqReady := Mux1H(
    Seq(
      extendType       -> crossWriteState.andR,
      crossDataType    -> (!remainder.orR && executeStageDeqFire),
      maskPipeIsReduce -> (stateIdle || stateWaitNew)
    )
  )

  // update register
  val secondReqReg:     ValidIO[LaneStage3Enqueue] = RegInit(0.U.asTypeOf(Valid(new LaneStage3Enqueue(parameter, true))))
  val secondMessageReg: PipeForMaskUnit            = RegInit(0.U.asTypeOf(new PipeForMaskUnit(parameter)))

  val secondDeqReady: Bool = Wire(Bool())
  val secondDeqFire:  Bool = secondDeqReady && secondReqReg.valid
  val secondEnqFire:  Bool = secondReqQueue.deq.fire
  secondReqQueue.deq.ready := !secondReqReg.valid || secondDeqReady

  when(secondEnqFire) {
    secondReqReg.bits := secondReqQueue.deq.bits.req
    secondMessageReg  := secondReqQueue.deq.bits.maskPipe
  }

  when(secondEnqFire ^ secondDeqFire) {
    secondReqReg.valid := secondEnqFire
  }

  val secondPipeSameLane: Bool = secondReqReg.bits.pipeForSecondPipe.get.writeSink === laneIndex
  val gatherWriteData:    Bits = secondMessageReg.source2 >> (secondReqReg.bits.pipeForSecondPipe.get.readOffset ## 0.U(
    3.W
  )) << (secondReqReg.bits.pipeForSecondPipe.get.writeOffset ## 0.U(3.W))
  val gatherWriteMask = (writeMask << secondReqReg.bits.pipeForSecondPipe.get.writeOffset).asUInt

  freeWriteArbiter.io.in(2).valid             := secondReqReg.valid && secondPipeSameLane
  freeWriteArbiter.io.in(2).bits.data         := gatherWriteData
  freeWriteArbiter.io.in(2).bits.mask         := gatherWriteMask
  freeWriteArbiter.io.in(2).bits.groupCounter := secondReqReg.bits.pipeForSecondPipe.get.writeCounter

  freeCrossDataUsedBySecondPipe := secondReqReg.valid && !secondPipeSameLane
  when(secondReqReg.valid && !secondPipeSameLane) {
    freeCrossDataDeq.valid        := true.B
    freeCrossDataDeq.bits.data    := gatherWriteData
    freeCrossDataDeq.bits.mask    := gatherWriteMask
    freeCrossDataDeq.bits.counter := secondReqReg.bits.pipeForSecondPipe.get.writeCounter
    freeCrossDataDeq.bits.sink    := secondReqReg.bits.pipeForSecondPipe.get.writeSink
  }

  secondDeqReady := Mux(
    secondPipeSameLane,
    freeWriteArbiter.io.in(2).ready && !freeCrossDataEnq.valid,
    freeCrossDataDeq.ready
  )

  // reduce execution
  reduceVRFRequest.valid := stateSRequest || stateFold || stateOrderFold
  val lastFoldSource1 = (reduceResult >> ((1.U << reduceResultSize >> 1).asUInt ## 0.U(3.W))).asUInt
  val source2Select: UInt = Mux(stateFold || stateOrderFold, lastFoldSource1, maskPipeReqReg.data)
  val reduceIsPopCount = maskPipeReqReg.decodeResult(Decoder.popCount)
  val writeEEW: UInt =
    Mux(reduceIsPopCount, 2.U, OHToUInt(maskPipeMessageReg.sew1H) + maskPipeReqReg.decodeResult(Decoder.widenReduce))

  reduceVRFRequest.bits          := DontCare
  reduceVRFRequest.bits.src.head := reduceResult
  reduceVRFRequest.bits.src(1)   := source2Select
  reduceVRFRequest.bits.src.last := -1.S(parameter.datapathWidth.W).asUInt
  reduceVRFRequest.bits.opcode   := Mux(reduceIsPopCount, 0.U, maskPipeReqReg.decodeResult(Decoder.uop))
  reduceVRFRequest.bits.vSew     := writeEEW
  reduceVRFRequest.bits.tag      := parameter.chainingSize.U
  reduceVRFRequest.bits.sign0    := !maskPipeReqReg.decodeResult(Decoder.unsigned0)
  reduceVRFRequest.bits.sign     := !maskPipeReqReg.decodeResult(Decoder.unsigned1)
  reduceVRFRequest.bits.reverse  := maskPipeReqReg.decodeResult(Decoder.reverse)
  reduceVRFRequest.bits.average  := maskPipeReqReg.decodeResult(Decoder.average)
  reduceVRFRequest.bits.saturate := maskPipeReqReg.decodeResult(Decoder.saturate)
  reduceVRFRequest.bits.vxrm     := maskPipeMessageReg.csr.vxrm
  reduceVRFRequest.bits.complete := false.B
  reduceVRFRequest.bits.unitSelet.foreach(_ := maskPipeReqReg.decodeResult(Decoder.fpExecutionType))
  reduceVRFRequest.bits.floatMul.foreach(_ := maskPipeReqReg.decodeResult(Decoder.floatMul))

  // from float csr
  reduceVRFRequest.bits.roundingMode.foreach(_ := maskPipeMessageReg.csr.frm)

  reduceRequestDecode := maskPipeReqReg.decodeResult
  // todo
  val reduceVRFResponseFire: Bool = reduceResponse.valid
  when(stateSRequest && reduceVRFRequest.ready) {
    reduceState := wResponse
  }
  when(stateWResponse && reduceVRFResponseFire) {
    reduceState := Mux(
      maskPipeIsOrder && !foldFinish,
      orderFold,
      sMaskRequest
    )
  }
  when(stateWLFR && reduceVRFResponseFire) {
    reduceState := Mux(
      foldFinish,
      sWrite,
      fold
    )
  }
  when(stateOrderWait && reduceVRFResponseFire) {
    reduceState := Mux(
      foldFinish,
      Mux(hitLast && firstLane, sWrite, sMaskRequest),
      orderFold
    )
  }

  val cutResponse = cutUIntBySize(reduceResponse.bits.data, parameter.laneScale)
  val cutResult   = cutUIntBySize(reduceResult, parameter.laneScale)
  val fpValid     = maskPipeReqReg.fpReduceValid.getOrElse(0.U) | Fill(parameter.laneScale, !maskPipeIsOrder)
  when(reduceVRFResponseFire) {
    reduceResult := VecInit(cutResponse.zipWithIndex.map { case (res, index) =>
      Mux(
        stateOrderWait,
        if (index == 0) {
          Mux(fpValid(parameter.laneScale - 1), res, cutResult(index))
        } else {
          0.U.asTypeOf(res)
        },
        Mux(fpValid(index), res, cutResult(index))
      )
    }).asUInt
  }

  val finishReport: Bool = RegNext(
    reduceMaskResponse.valid && reduceMaskResponse.bits.finish && !laneIndex.andR,
    false.B
  )
  reduceMaskRequest.valid        := stateSMaskRequest || stateSWrite || finishReport
  reduceMaskRequest.bits.data    := reduceResult
  reduceMaskRequest.bits.hitLast := hitLast
  reduceMaskRequest.bits.finish  := finish || stateSWrite || finishReport
  when(stateSMaskRequest && reduceMaskRequest.ready) {
    reduceState := Mux(
      maskPipeIsOrder,
      Mux(hitLast, wMaskRequest, waitNewGroup),
      Mux(firstLane, wMaskRequest, idle)
    )
  }

  val needFold:        Bool =
    if (parameter.laneScale > 1) true.B
    else
      (maskPipeMessageReg.sew1H(0) || (maskPipeMessageReg.sew1H(1) &&
        !maskPipeReqReg.decodeResult(Decoder.widenReduce))) &&
      !maskPipeReqReg.decodeResult(Decoder.popCount)
  val sew1HCorrection: UInt =
    (maskPipeMessageReg.sew1H << maskPipeReqReg.decodeResult(Decoder.widenReduce)).asUInt(2, 0)
  foldFinish               := ((1.U << reduceResultSize).asUInt & sew1HCorrection).orR
  reduceMaskResponse.ready := stateWMaskRequest || stateIdle
  val willHitLast: Bool = reduceMaskResponse.bits.hitLast | hitLast
  when(stateWMaskRequest && reduceMaskResponse.fire) {
    reduceState      :=
      Mux(
        maskPipeIsOrder,
        Mux(
          reduceMaskResponse.bits.finish,
          idle,
          Mux(
            reduceMaskResponse.bits.hitLast,
            Mux(firstLane, Mux(foldFinish, sWrite, fold), sMaskRequest),
            sRequest
          )
        ),
        Mux(firstLane, Mux(foldFinish, sWrite, fold), sRequest)
      )
    reduceResultSize := log2Ceil(parameter.datapathWidth / 8).U
    reduceResult     := reduceMaskResponse.bits.data
    hitLast          := willHitLast
  }

  when(stateWaitNew) {
    reduceState := wMaskRequest
  }

  when(stateFold && reduceVRFRequest.fire) {
    reduceResultSize := reduceResultSize - 1.U
    reduceState      := waitLastFoldResponse
  }
  when(stateOrderFold && reduceVRFRequest.fire) {
    reduceResultSize := reduceResultSize - 1.U
    reduceState      := orderWaitResponse
  }

  freeWriteArbiter.io.in(3).valid             := stateSWrite && maskPipeIsReduce && !maskPipeIsPop
  freeWriteArbiter.io.in(3).bits.data         := reduceResult
  freeWriteArbiter.io.in(3).bits.mask         := sew1HCorrection(2) ## sew1HCorrection(2) ## !sew1HCorrection(0) ## true.B
  freeWriteArbiter.io.in(3).bits.groupCounter := 0.U

  val reduceDeq: Bool = freeWriteArbiter.io.in(3).ready
  when(stateSWrite && reduceDeq) {
    reduceState := idle
  }

  // for token
  val maskStageValid:     Bool = maskPipeValid || slideRequestReg1.valid || gatherRequestReg1.valid || !stateIdle ||
    crossLaneWriteQueue.map(_.deq.valid).reduce(_ || _)
  val maskStageValidNext: Bool = RegNext(maskStageValid, false.B)
  token.freeCrossWrite.valid          := crossLaneWriteQueue.last.enq.fire && crossLaneWriteQueue.last.enq.bits.mask.orR
  token.freeCrossWrite.bits           := maskPipeReqReg.instructionIndex
  token.maskStageRequestRelease.valid := maskReqQueue.deq.fire
  token.maskStageRequestRelease.bits  := maskReqQueue.deq.bits.req.instructionIndex
  token.maskStageClear                := !maskStageValid && maskStageValidNext
  val validCheck: Bool = {
    val maskPipeValid   = RegInit(false.B)
    when(normalEnqFire) {
      maskPipeValid := true.B
    }
    val tokenValidCheck = ohCheck(instructionValid, maskPipeReqReg.instructionIndex, parameter.chainingSize)
    when(maskPipeValid && !tokenValidCheck) {
      maskPipeValid := false.B
    }
    maskPipeValid
  }
  maskStageNoConflict := !validCheck || maskReqQueue.deq.bits.req.instructionIndex === maskPipeReqReg.instructionIndex
}
