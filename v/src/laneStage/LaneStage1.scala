package v

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode.DecodeBundle

class LaneStage1Enqueue(parameter: LaneParameter, isLastSlot: Boolean) extends Bundle {
  val groupCounter: UInt = UInt(parameter.groupNumberBits.W)
  val mask: UInt = UInt((parameter.datapathWidth / 8).W)
  val sSendResponse: Option[Bool] = Option.when(isLastSlot)(Bool())
}

class LaneStage1Dequeue(parameter: LaneParameter, isLastSlot: Boolean) extends Bundle {
  /** for dequeue group counter match */
  val readBusDequeueGroup: Option[UInt] = Option.when(isLastSlot)(UInt(parameter.groupNumberBits.W))
  val maskForFilter: UInt = UInt((parameter.datapathWidth / 8).W)
  val mask: UInt = UInt((parameter.datapathWidth / 8).W)
  val groupCounter: UInt = UInt(parameter.groupNumberBits.W)
  val sSendResponse: Option[Bool] = Option.when(isLastSlot)(Bool())
  // read result
  val src: Vec[UInt] = Vec(3, UInt(parameter.datapathWidth.W))
  val crossReadSource: Option[UInt] = Option.when(isLastSlot)(UInt((parameter.datapathWidth * 2).W))
}

/** 这一个stage 分两级流水, 分别是 读vrf 等vrf结果
 * */
class LaneStage1(parameter: LaneParameter, isLastSlot: Boolean) extends
  LaneStage(true)(
    new LaneStage1Enqueue(parameter, isLastSlot),
    new LaneStage1Dequeue(parameter, isLastSlot)
  ) {
  val state: LaneState = IO(Input(new LaneState(parameter)))
  val vrfReadRequest: Vec[DecoupledIO[VRFReadRequest]] = IO(
    Vec(
      3,
      Decoupled(
        new VRFReadRequest(parameter.vrfParam.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits)
      )
    )
  )

  /** VRF read result for each slot,
   * 3 is for [[source1]] [[source2]] [[source3]]
   */
  val vrfReadResult: Vec[UInt] = IO(Input(Vec(3, UInt(parameter.datapathWidth.W))))

  val readBusDequeue: Option[ValidIO[ReadBusData]] = Option.when(isLastSlot)(IO(
    Flipped(Valid(new ReadBusData(parameter: LaneParameter)))
  ))

  val readBusRequest: Option[DecoupledIO[ReadBusData]] =
    Option.when(isLastSlot)(IO(Decoupled(new ReadBusData(parameter))))
  val readFromScalar: UInt = IO(Input(UInt(parameter.datapathWidth.W)))

  val pipeEnqueue: LaneStage1Enqueue = RegInit(0.U.asTypeOf(enqueue.bits))

  val maskedWrite: Bool = WireDefault(false.B)

  // read state
  /** schedule read src1 */
  val sRead0: Bool = RegInit(true.B)

  /** schedule read src2 */
  val sRead1: Bool = RegInit(true.B)

  /** schedule read vd */
  val sRead2: Bool = RegInit(true.B)

  // pipe read result
  val readResult0: UInt = RegInit(0.U(parameter.datapathWidth.W))
  val readResult1: UInt = RegInit(0.U(parameter.datapathWidth.W))
  val readResult2: UInt = RegInit(0.U(parameter.datapathWidth.W))

  val crossReadLSBReg: Option[UInt] = Option.when(isLastSlot)(RegInit(0.U(parameter.datapathWidth.W)))
  val crossReadMSBReg: Option[UInt] = Option.when(isLastSlot)(RegInit(0.U(parameter.datapathWidth.W)))

  val crossReadLSBIn: Option[UInt] = Option.when(isLastSlot)(RegInit(0.U(parameter.datapathWidth.W)))
  val crossReadMSBIn: Option[UInt] = Option.when(isLastSlot)(RegInit(0.U(parameter.datapathWidth.W)))

  // state for cross read
  /** schedule cross lane read LSB.(access VRF for cross read) */
  val sCrossReadLSB: Option[Bool] = Option.when(isLastSlot)(RegInit(true.B))

  /** schedule cross lane read MSB.(access VRF for cross read) */
  val sCrossReadMSB: Option[Bool] = Option.when(isLastSlot)(RegInit(true.B))

  /** schedule send cross lane read LSB result. */
  val sSendCrossReadResultLSB: Option[Bool] = Option.when(isLastSlot)(RegInit(true.B))

  /** schedule send cross lane read MSB result. */
  val sSendCrossReadResultMSB: Option[Bool] = Option.when(isLastSlot)(RegInit(true.B))

  /** wait for cross lane read LSB result. */
  val wCrossReadLSB: Option[Bool] = Option.when(isLastSlot)(RegInit(true.B))

  /** wait for cross lane read MSB result. */
  val wCrossReadMSB: Option[Bool] = Option.when(isLastSlot)(RegInit(true.B))

  // next for update cross read register
  val sReadNext0: Bool = RegNext(sRead0, false.B)
  val sReadNext1: Bool = RegNext(sRead1, false.B)
  val sReadNext2: Bool = RegNext(sRead2, false.B)
  val sCrossReadLSBNext: Option[Bool] = sCrossReadLSB.map(RegNext(_, false.B))
  val sCrossReadMSBNext: Option[Bool] = sCrossReadMSB.map(RegNext(_, false.B))

  // All read requests sent
  val sReadFinish: Bool = sRead0 && sRead1 && sRead2
  // Waiting to read the response
  val sReadFinishNext: Bool = sReadNext0 && sReadNext1 && sReadNext2
  // 'sReadFinishNext' may assert at the next cycle of 's1Fire', need sReadFinish
  val readFinish: Bool = sReadFinish && sReadFinishNext
  stageFinish := (Seq(readFinish) ++ sSendCrossReadResultLSB ++
    sSendCrossReadResultMSB ++ wCrossReadLSB ++ wCrossReadMSB).reduce(_ && _)

  // read vrf
  // read port 0
  vrfReadRequest(0).valid := !sRead0 && stageValidReg
  vrfReadRequest(0).bits.offset := pipeEnqueue.groupCounter(parameter.vrfOffsetBits - 1, 0)
  vrfReadRequest(0).bits.vs := Mux(
    // encodings with vm=0 are reserved for mask type logic
    state.decodeResult(Decoder.maskLogic) && !state.decodeResult(Decoder.logic),
    // read v0 for (15. Vector Mask Instructions)
    0.U,
    state.vs1 + pipeEnqueue.groupCounter(
      parameter.groupNumberBits - 1,
      parameter.vrfOffsetBits
    )
  )
  // used for hazard detection
  vrfReadRequest(0).bits.instructionIndex := state.instructionIndex

  // read port 1
  if (isLastSlot) {
    vrfReadRequest(1).valid := !(sRead1 && sCrossReadLSB.get) && stageValidReg
    vrfReadRequest(1).bits.offset := Mux(
      sRead1,
      // cross lane LSB
      pipeEnqueue.groupCounter(parameter.vrfOffsetBits - 2, 0) ## false.B,
      // normal read
      pipeEnqueue.groupCounter(parameter.vrfOffsetBits - 1, 0)
    )
    vrfReadRequest(1).bits.vs := Mux(
      state.decodeResult(Decoder.vwmacc) && sRead1,
      // cross read vd for vwmacc, since it need dual [[dataPathWidth]], use vs2 port to read LSB part of it.
      state.vd,
      // read vs2 for other instruction
      state.vs2
    ) + Mux(
      sRead1,
      // cross lane
      pipeEnqueue.groupCounter(parameter.groupNumberBits - 2, parameter.vrfOffsetBits - 1),
      // no cross lane
      pipeEnqueue.groupCounter(parameter.groupNumberBits - 1, parameter.vrfOffsetBits)
    )
  } else {
    vrfReadRequest(1).valid := !sRead1 && stageValidReg
    vrfReadRequest(1).bits.offset := pipeEnqueue.groupCounter(parameter.vrfOffsetBits - 1, 0)
    vrfReadRequest(1).bits.vs := state.vs2 +
      pipeEnqueue.groupCounter(parameter.groupNumberBits - 1, parameter.vrfOffsetBits)
  }
  vrfReadRequest(1).bits.instructionIndex := state.instructionIndex

  // read port 2
  if (isLastSlot) {
    vrfReadRequest(2).valid := !(sRead2 && sCrossReadMSB.get) && stageValidReg
    vrfReadRequest(2).bits.offset := Mux(
      sRead2,
      // cross lane MSB
      pipeEnqueue.groupCounter(parameter.vrfOffsetBits - 2, 0) ## true.B,
      // normal read
      pipeEnqueue.groupCounter(parameter.vrfOffsetBits - 1, 0)
    )
    vrfReadRequest(2).bits.vs := Mux(
      sRead2 && !state.decodeResult(Decoder.vwmacc),
      // cross lane access use vs2
      state.vs2,
      // normal read vd or cross read vd for vwmacc
      state.vd
    ) +
      Mux(
        sRead2,
        pipeEnqueue.groupCounter(parameter.groupNumberBits - 2, parameter.vrfOffsetBits - 1),
        pipeEnqueue.groupCounter(parameter.groupNumberBits - 1, parameter.vrfOffsetBits)
      )
  } else {
    vrfReadRequest(2).valid := !sRead2 && stageValidReg
    vrfReadRequest(2).bits.offset := pipeEnqueue.groupCounter(parameter.vrfOffsetBits - 1, 0)
    vrfReadRequest(2).bits.vs := state.vd +
      pipeEnqueue.groupCounter(parameter.groupNumberBits - 1, parameter.vrfOffsetBits)
  }
  vrfReadRequest(2).bits.instructionIndex := state.instructionIndex

  val readPortFire0: Bool = vrfReadRequest(0).fire
  val readPortFire1: Bool = vrfReadRequest(1).fire
  val readPortFire2: Bool = vrfReadRequest(2).fire
  // reg next for update result
  val readPortFireNext0: Bool = RegNext(readPortFire0, false.B)
  val readPortFireNext1: Bool = RegNext(readPortFire1, false.B)
  val readPortFireNext2: Bool = RegNext(readPortFire2, false.B)

  // init state
  when(enqueue.fire) {
    pipeEnqueue := enqueue.bits
    sRead0 := !state.decodeResult(Decoder.vtype)
    sRead1 := false.B
    // todo: mask write need read vd
    sRead2 := state.decodeResult(Decoder.sReadVD)
    val sCrossRead = !state.decodeResult(Decoder.crossRead)
    (sCrossReadLSB ++ sCrossReadMSB ++ sSendCrossReadResultLSB ++
      sSendCrossReadResultMSB ++ wCrossReadLSB ++ wCrossReadMSB).foreach(s => s := sCrossRead)
  }.otherwise {
    when(readPortFire0) {
      sRead0 := true.B
    }
    // the priority of `sRead1` is higher than `sCrossReadLSB`
    when(readPortFire1) {
      sRead1 := true.B
      sCrossReadLSB.foreach(d => d := sRead1)
    }
    // the priority of `sRead2` is higher than `sCrossReadMSB`
    when(readPortFire2) {
      sRead2 := true.B
      sCrossReadMSB.foreach(d => d := sRead2)
    }

    readBusDequeue.foreach { crossReadDequeue =>
      when(crossReadDequeue.valid) {
        when(crossReadDequeue.bits.isTail) {
          wCrossReadMSB.foreach(_ := true.B)
          crossReadMSBIn.foreach(_ := crossReadDequeue.bits.data)
        }.otherwise {
          wCrossReadLSB.foreach(_ := true.B)
          crossReadLSBIn.foreach(_ := crossReadDequeue.bits.data)
        }
      }
    }
  }

  // update read result register
  when(readPortFireNext0) {
    readResult0 := vrfReadResult(0)
  }

  when(readPortFireNext1) {
    if (isLastSlot) {
      when(sReadNext1) {
        crossReadLSBReg.foreach(d => d := vrfReadResult(1))
      }.otherwise {
        readResult1 := vrfReadResult(1)
      }
    } else {
      readResult1 := vrfReadResult(1)
    }
  }

  when(readPortFireNext2) {
    if (isLastSlot) {
      when(sReadNext2) {
        crossReadMSBReg.foreach(d => d := vrfReadResult(2))
      }.otherwise {
        readResult2 := vrfReadResult(2)
      }
    } else {
      readResult2 := vrfReadResult(2)
    }
  }

  // connect cross read
  if (isLastSlot) {
    dequeue.bits.readBusDequeueGroup.foreach(d => d := pipeEnqueue.groupCounter)

    val crossLaneRead: DecoupledIO[ReadBusData] = Wire(Decoupled(new ReadBusData(parameter)))
    /** The data to be sent is ready
     * need sCrossReadLSB since sCrossReadLSBNext may assert after s1fire.
     */
    val crossReadDataReadyLSB: Bool = (sCrossReadLSBNext ++ sCrossReadLSB).reduce(_ && _)
    val crossReadDataReadyMSB: Bool = (sCrossReadMSBNext ++ sCrossReadMSB).reduce(_ && _)

    /** read data from RF, try to send cross lane read LSB data to ring */
    val tryCrossReadSendLSB: Bool = crossReadDataReadyLSB && !sSendCrossReadResultLSB.get && stageValidReg

    /** read data from RF, try to send cross lane read MSB data to ring */
    val tryCrossReadSendMSB: Bool = crossReadDataReadyMSB && !sSendCrossReadResultMSB.get && stageValidReg

    crossLaneRead.bits.sinkIndex := (!tryCrossReadSendLSB) ## state.laneIndex(parameter.laneNumberBits - 1, 1)
    crossLaneRead.bits.isTail := state.laneIndex(0)
    crossLaneRead.bits.sourceIndex := state.laneIndex
    crossLaneRead.bits.instructionIndex := state.instructionIndex
    crossLaneRead.bits.counter := pipeEnqueue.groupCounter
    // TODO: use [[record.state.sSendCrossReadResultLSB]] -> MSB may be ready earlier
    crossLaneRead.bits.data := Mux(tryCrossReadSendLSB, crossReadLSBReg.get, crossReadMSBReg.get)
    crossLaneRead.valid := tryCrossReadSendLSB || tryCrossReadSendMSB
    readBusRequest.foreach(_ <> crossLaneRead)

    when(crossLaneRead.fire) {
      when(tryCrossReadSendLSB) {
        sSendCrossReadResultLSB.foreach(_ := true.B)
      }.otherwise {
        sSendCrossReadResultMSB.foreach(_ := true.B)
      }
    }
  }

  val source1Select: UInt = Mux(state.decodeResult(Decoder.vtype), readResult0, readFromScalar)
  dequeue.bits.mask := pipeEnqueue.mask
  dequeue.bits.groupCounter := pipeEnqueue.groupCounter
  dequeue.bits.src := VecInit(Seq(source1Select, readResult1, readResult2))
  dequeue.bits.crossReadSource.foreach(_ := crossReadMSBIn.get ## crossReadLSBIn.get)
  dequeue.bits.sSendResponse.foreach(_ := pipeEnqueue.sSendResponse.get)

  dequeue.bits.maskForFilter := FillInterleaved(4, state.maskNotMaskedElement) | pipeEnqueue.mask

  when(enqueue.fire ^ dequeue.fire) {
    stageValidReg := enqueue.fire
  }
}
