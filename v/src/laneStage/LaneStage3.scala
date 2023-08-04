package v

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode.DecodeBundle

class LaneStage3Enqueue(parameter: LaneParameter) extends Bundle {
  val groupCounter: UInt = UInt(parameter.groupNumberBits.W)
  val data: UInt = UInt(parameter.datapathWidth.W)
  val pipeData: UInt = UInt(parameter.datapathWidth.W)
  val mask: UInt = UInt((parameter.datapathWidth/8).W)
  val ffoIndex: UInt = UInt(log2Ceil(parameter.vLen / 8).W)
  val crossWriteData: Vec[UInt] = Vec(2, UInt(parameter.datapathWidth.W))
  val sSendResponse: Bool = Bool()
  val ffoSuccess: Bool = Bool()
}

class LaneStage3(parameter: LaneParameter, isLastSlot: Boolean) extends Module {
  val enqueue: DecoupledIO[LaneStage3Enqueue] = IO(Flipped(Decoupled(new LaneStage3Enqueue(parameter))))
  val vrfWriteBundle: VRFWriteRequest = new VRFWriteRequest(
    parameter.vrfParam.regNumBits,
    parameter.vrfOffsetBits,
    parameter.instructionIndexBits,
    parameter.datapathWidth
  )
  val vrfWriteRequest: DecoupledIO[VRFWriteRequest] = IO(Decoupled(vrfWriteBundle))

  val state: LaneState = IO(Input(new LaneState(parameter)))
  val pipeEnqueue: Option[LaneStage3Enqueue] = Option.when(isLastSlot)(RegInit(0.U.asTypeOf(enqueue.bits)))
  /** response to [[V.lsu]] or mask unit in [[V]] */
  val laneResponse: Option[ValidIO[LaneResponse]] = Option.when(isLastSlot)(IO(Valid(new LaneResponse(parameter))))
  val stageValid: Bool = IO(Output(Bool()))
  /** feedback from [[V]] to [[Lane]] for [[laneResponse]] */
  val laneResponseFeedback: Option[ValidIO[LaneResponseFeedback]] =
    Option.when(isLastSlot)(IO(Flipped(Valid(new LaneResponseFeedback(parameter)))))
  val crossWritePort: Option[DecoupledIO[WriteBusData]] =
    Option.when(isLastSlot)(IO(Decoupled(new WriteBusData(parameter))))

  val stageValidReg: Option[Bool] = Option.when(isLastSlot) (RegInit(false.B))

  /** schedule cross lane write LSB */
  val sCrossWriteLSB: Option[Bool] = Option.when(isLastSlot)(RegInit(true.B))

  /** schedule cross lane write MSB */
  val sCrossWriteMSB: Option[Bool] = Option.when(isLastSlot)(RegInit(true.B))

  // state for response to scheduler
  /** schedule send [[LaneResponse]] to scheduler */
  val sSendResponse: Option[Bool] = Option.when(isLastSlot)(RegInit(true.B))

  /** wait scheduler send [[LaneResponseFeedback]] */
  val wResponseFeedback: Option[Bool] = Option.when(isLastSlot)(RegInit(true.B))

  // update register
  when(enqueue.fire) {
    pipeEnqueue.foreach(_ := enqueue.bits)
    (sCrossWriteLSB ++ sCrossWriteMSB).foreach(_ := !state.decodeResult(Decoder.crossWrite))
    (sSendResponse ++ wResponseFeedback).foreach(
      _ := state.decodeResult(Decoder.scheduler) || enqueue.bits.sSendResponse
    )
  }

  val vrfWriteQueue: Queue[VRFWriteRequest] =
    Module(new Queue(vrfWriteBundle, entries = 1, pipe = false, flow = false))

  /** Write queue ready or not need to write. */
  val vrfWriteReady: Bool = vrfWriteQueue.io.enq.ready || state.decodeResult(Decoder.sWrite)

  if (isLastSlot) {
    // VRF cross write
    /** data for enqueuing [[writeBusPort]]
     * [[crossLaneWrite.valid]] indicate there is a slot try to enqueue [[writeBusPort]]
     */
    val crossLaneWrite: DecoupledIO[WriteBusData] = Wire(Decoupled(new WriteBusData(parameter)))
    crossWritePort.get <> crossLaneWrite

    /** execute in ALU, try to send cross lane write LSB data to ring */
    val tryCrossWriteSendLSB = stageValidReg.get && !sCrossWriteLSB.get

    /** execute in ALU, try to send cross lane write MSB data to ring */
    val tryCrossWriteSendMSB = stageValidReg.get && !sCrossWriteMSB.get
    crossLaneWrite.bits.sinkIndex := state.laneIndex(parameter.laneNumberBits - 2, 0) ## (!tryCrossWriteSendLSB)
    crossLaneWrite.bits.sourceIndex := state.laneIndex
    crossLaneWrite.bits.isTail := state.laneIndex(parameter.laneNumberBits - 1)
    crossLaneWrite.bits.instructionIndex := state.instructionIndex
    crossLaneWrite.bits.counter := pipeEnqueue.get.groupCounter
    crossLaneWrite.bits.data :=
      Mux(tryCrossWriteSendLSB, pipeEnqueue.get.crossWriteData.head, pipeEnqueue.get.crossWriteData.last)
    crossLaneWrite.bits.mask := Mux(tryCrossWriteSendLSB, pipeEnqueue.get.mask(1, 0), pipeEnqueue.get.mask(3, 2))
    crossLaneWrite.valid := tryCrossWriteSendLSB || tryCrossWriteSendMSB

    when(crossLaneWrite.fire) {
      sCrossWriteLSB.foreach(_ := true.B)
      when(sCrossWriteLSB.get) {
        sCrossWriteMSB.foreach(_ := true.B)
      }
    }
    // scheduler synchronization
    val schedulerFinish: Bool = (sSendResponse ++ wResponseFeedback).reduce(_ && _)

    val dataSelect: Option[UInt] = Option.when(isLastSlot) {
      Mux(state.decodeResult(Decoder.nr) || state.ffoByOtherLanes || state.decodeResult(Decoder.dontNeedExecuteInLane),
        pipeEnqueue.get.pipeData,
        pipeEnqueue.get.data
      )
    }
    // mask request
    laneResponse.head.valid := stageValidReg.get && !sSendResponse.get
    laneResponse.head.bits.data := Mux(state.decodeResult(Decoder.ffo), pipeEnqueue.get.ffoIndex, dataSelect.get)
    laneResponse.head.bits.toLSU := state.loadStore
    laneResponse.head.bits.instructionIndex := state.instructionIndex
    laneResponse.head.bits.ffoSuccess := pipeEnqueue.get.ffoSuccess

    sSendResponse.foreach(state => when(laneResponse.head.valid) {
      state := true.B
    })
    wResponseFeedback.foreach(state => when(laneResponseFeedback.head.valid) {
      state := true.B
    })

    // enqueue write for last slot
    vrfWriteQueue.io.enq.valid := stageValidReg.get && schedulerFinish && !state.decodeResult(Decoder.sWrite)

    // UInt(5.W) + UInt(3.W), use `+` here
    vrfWriteQueue.io.enq.bits.vd := state.vd + pipeEnqueue.get.groupCounter(
      parameter.groupNumberBits - 1,
      parameter.vrfOffsetBits
    )

    vrfWriteQueue.io.enq.bits.offset := pipeEnqueue.get.groupCounter
    vrfWriteQueue.io.enq.bits.data := dataSelect.get
    vrfWriteQueue.io.enq.bits.last := DontCare
    vrfWriteQueue.io.enq.bits.instructionIndex := state.instructionIndex
    vrfWriteQueue.io.enq.bits.mask := pipeEnqueue.get.mask

    // Handshake
    /** Cross-lane writing is over */
    val CrossLaneWriteOver: Bool = (sCrossWriteLSB ++ sCrossWriteMSB).reduce(_ && _)

    enqueue.ready := !stageValidReg.get || (CrossLaneWriteOver && schedulerFinish && vrfWriteReady)
    val dequeueFire = stageValidReg.get && CrossLaneWriteOver && schedulerFinish && vrfWriteReady
    stageValidReg.foreach{data =>
      when(dequeueFire ^ enqueue.fire) {
        data := enqueue.fire
      }
    }
    stageValid := stageValidReg.get || vrfWriteQueue.io.deq.valid
  } else {
    // Normal will be one level less
    vrfWriteQueue.io.enq.valid := enqueue.valid

    // UInt(5.W) + UInt(3.W), use `+` here
    vrfWriteQueue.io.enq.bits.vd := state.vd + enqueue.bits.groupCounter(
      parameter.groupNumberBits - 1,
      parameter.vrfOffsetBits
    )

    vrfWriteQueue.io.enq.bits.offset := enqueue.bits.groupCounter

    vrfWriteQueue.io.enq.bits.data := enqueue.bits.data
    vrfWriteQueue.io.enq.bits.last := DontCare
    vrfWriteQueue.io.enq.bits.instructionIndex := state.instructionIndex
    vrfWriteQueue.io.enq.bits.mask := enqueue.bits.mask

    // Handshake
    enqueue.ready := vrfWriteQueue.io.enq.ready
    stageValid := vrfWriteQueue.io.deq.valid
  }
  vrfWriteRequest <> vrfWriteQueue.io.deq
}