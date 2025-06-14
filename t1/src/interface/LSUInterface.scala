// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental._
import chisel3.util._
import org.chipsalliance.t1.rtl.decoder.DecoderParam

case class LSUIFParameter(
  vLen:          Int,
  eLen:          Int,
  datapathWidth: Int,
  laneNumber:    Int,
  chainingSize:  Int,
  fpuEnable:     Boolean,
  decoderParam:  DecoderParam)
    extends SerializableModuleParameter {

  val laneScale: Int = datapathWidth / eLen

  val chaining1HBits: Int = 2 << log2Ceil(chainingSize)

  /** 1 in MSB for instruction order. */
  val instructionIndexBits: Int = log2Ceil(chainingSize) + 1

  /** maximum of lmul, defined in spec. */
  val lmulMax: Int = 8

  /** see [[T1Parameter.sewMin]] */
  val sewMin: Int = 8

  /** The datapath is divided into groups based on SEW. The current implement is calculating them in multiple cycles.
    * TODO: we must parallelize it as much as possible since it highly affects the computation bandwidth.
    */
  val dataPathByteWidth: Int = datapathWidth / sewMin

  val dataPathByteBits: Int = log2Ceil(dataPathByteWidth)

  /** maximum of vl. */
  val vlMax: Int = vLen * lmulMax / sewMin

  /** width of [[vlMax]] `+1` is for vl being 0 to vlMax(not vlMax - 1). we use less than for comparing, rather than
    * less equal.
    */
  val vlMaxBits: Int = log2Ceil(vlMax) + 1

  /** how many group does a single register have.
    *
    * in each lane, for one vector register, it is divided into groups with size of [[datapathWidth]]
    */
  val singleGroupSize: Int = vLen / datapathWidth / laneNumber

  /** for each instruction, the maximum number of groups to execute. */
  val groupNumberMax: Int = singleGroupSize * lmulMax

  /** used as the LSB index of VRF access
    *
    * TODO: uarch doc the arrangement of VRF: {reg index, offset}
    *
    * for each number in table below, it represent a [[datapathWidth]]
    * {{{
    *         lane0 | lane1 | ...                                   | lane8
    * offset0    0  |    1  |    2  |    3  |    4  |    5  |    6  |    7
    * offset1    8  |    9  |   10  |   11  |   12  |   13  |   14  |   15
    * offset2   16  |   17  |   18  |   19  |   20  |   21  |   22  |   23
    * offset3   24  |   25  |   26  |   27  |   28  |   29  |   30  |   31
    * }}}
    */
  val vrfOffsetBits: Int = log2Ceil(singleGroupSize)

  /** +1 in MSB for comparing to next group number. we don't use `vl-1` is because if the mask of last group is all 0,
    * it should be jumped through. so we directly compare the next group number with the MSB of `vl`.
    */
  val groupNumberBits: Int = log2Ceil(groupNumberMax + 1)

  /** hardware width of [[laneNumber]]. */
  val laneNumberBits: Int = 1.max(log2Ceil(laneNumber))

  /** hardware width of [[datapathWidth]]. */
  val datapathWidthBits: Int = log2Ceil(datapathWidth)

  /** see [[T1Parameter.maskGroupWidth]] */
  val maskGroupWidth: Int = datapathWidth

  /** see [[T1Parameter.maskGroupSize]] */
  val maskGroupSize: Int = vLen / maskGroupWidth

  /** hardware width of [[maskGroupSize]]. */
  val maskGroupSizeBits: Int = log2Ceil(maskGroupSize)

  /** Size of the queue for storing execution information todo: Determined by the longest execution unit
    */
  val executionQueueSize: Int = 4

  // outstanding of MaskExchangeUnit.maskReq
  val maskRequestQueueSize: Int = 8

  /** VRF index number is 32, defined in spec. */
  val regNum: Int = 32

  /** The hardware width of [[regNum]] */
  val regNumBits: Int = log2Ceil(regNum)

  val dataWidth: Int = new LaneRequest(
    instructionIndexBits,
    decoderParam,
    datapathWidth,
    vlMaxBits,
    laneNumber,
    dataPathByteWidth
  ).getWidth
  val lsuSize = 1

  // lane + lsu + top + mask unit
  val idWidth:     Int = log2Ceil(laneNumber + lsuSize + 1 + 1)
  // todo
  val opcodeWidth: Int = log2Ceil(9)
}

class LSUInterfaceIO(parameter: LSUIFParameter) extends Bundle {
  val clock: Clock = Input(Clock())
  val reset: Reset = Input(Reset())

  // physical channel: if <-> lsu

  // lsu -> if(this)

  // opcode 1
  val vrfReadRequest: Vec[DecoupledIO[VRFReadRequest]] = Vec(
    parameter.laneNumber,
    Flipped(
      Decoupled(
        new VRFReadRequest(parameter.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits)
      )
    )
  )

  // opcode 5
  val lsuReport: UInt = Input(UInt(parameter.chaining1HBits.W))
  val dataInWriteQueue = Input(Vec(parameter.laneNumber, UInt(parameter.chaining1HBits.W)))

  // opcode 6
  val vrfWriteRequest: Vec[DecoupledIO[VRFWriteRequest]] = Vec(
    parameter.laneNumber,
    Flipped(
      Decoupled(
        new VRFWriteRequest(
          parameter.regNumBits,
          parameter.vrfOffsetBits,
          parameter.instructionIndexBits,
          parameter.datapathWidth
        )
      )
    )
  )

  // if(this) -> lsu

  // opcode 1
  val readVrfAck: Vec[DecoupledIO[UInt]] = Vec(parameter.laneNumber, Decoupled(UInt(parameter.datapathWidth.W)))

  // opcode 3
  val maskUnitRequest: Vec[DecoupledIO[MaskUnitExeReq]] = Vec(
    parameter.laneNumber,
    Decoupled(
      new MaskUnitExeReq(parameter.eLen, parameter.datapathWidth, parameter.instructionIndexBits, parameter.fpuEnable)
    )
  )

  // opcode 5
  val v0Update: Vec[DecoupledIO[V0Update]] =
    Vec(parameter.laneNumber, Decoupled(new V0Update(parameter.datapathWidth, parameter.vrfOffsetBits)))

  // opcode 10
  val lsuWriteAck: Vec[DecoupledIO[UInt]] = Vec(parameter.laneNumber, Decoupled(UInt(parameter.instructionIndexBits.W)))
  // virtual channel: lane <-> if

  // lane -> if
  val inputVirtualChannelVec: Vec[Vec[DecoupledIO[LaneVirtualChannel]]] = Vec(
    4,
    Vec(
      parameter.laneNumber,
      Flipped(Decoupled(new LaneVirtualChannel(parameter.dataWidth, parameter.opcodeWidth, parameter.idWidth)))
    )
  )

  // if -> lane
  val outputVirtualChannelVec: Vec[Vec[DecoupledIO[LaneVirtualChannel]]] = Vec(
    3,
    Vec(
      parameter.laneNumber,
      Decoupled(new LaneVirtualChannel(parameter.dataWidth, parameter.opcodeWidth, parameter.idWidth))
    )
  )

  // this -> lsu PC
  // opcode 0
  val lsuRequest = Decoupled(new LSURequestInterface(parameter.eLen, parameter.chainingSize, parameter.vlMaxBits))

  // opcode 5
  val lsuReportToTop: DecoupledIO[LastReportBundle] = Flipped(Decoupled(new LastReportBundle(parameter.chaining1HBits)))

  val topInputVC:  Vec[DecoupledIO[LaneVirtualChannel]] =
    Vec(1, Flipped(Decoupled(new LaneVirtualChannel(parameter.dataWidth, parameter.opcodeWidth, parameter.idWidth))))
  val topOutputVC: Vec[DecoupledIO[LaneVirtualChannel]] =
    Vec(1, Decoupled(new LaneVirtualChannel(parameter.dataWidth, parameter.opcodeWidth, parameter.idWidth)))
}

class LSUInterface(val parameter: LSUIFParameter)
    extends FixedIORawModule(new LSUInterfaceIO(parameter))
    with SerializableModule[LSUIFParameter]
    with ImplicitClock
    with ImplicitReset {

  protected def implicitClock = io.clock
  protected def implicitReset = io.reset

  val dataInShifterVec: Seq[UInt] = io.vrfWriteRequest.zip(io.lsuWriteAck).zipWithIndex.map { case ((req, ack), i) =>
    val queueCount: Seq[UInt] = Seq.tabulate(parameter.chaining1HBits) { _ =>
      RegInit(0.U(2.W))
    }
    val enqOH:      UInt      = indexToOH(req.bits.instructionIndex, parameter.chainingSize)
    val queueEnq:   UInt      = Mux(req.fire, enqOH, 0.U)

    val queueDeq = Mux(ack.fire, indexToOH(ack.bits, parameter.chainingSize), 0.U)
    queueCount.zipWithIndex.foreach { case (count, index) =>
      val counterUpdate: UInt = Mux(queueEnq(index), 1.U, -1.S(2.W).asUInt)
      when(queueEnq(index) ^ queueDeq(index)) {
        count := count + counterUpdate
      }
    }
    VecInit(queueCount.map(_ =/= 0.U)).asUInt
  }
  // lsu <-> lane
  val physicalChannelFromLSU = Seq(io.vrfReadRequest, io.vrfWriteRequest)
  // Process opcode5 separately
  val opcodeFromLSU = Seq(1, 6)
  val vcIndex       = Seq(0, 2)

  physicalChannelFromLSU.zipWithIndex.foreach { case (pcVec, index) =>
    val outputVCVec: Vec[DecoupledIO[LaneVirtualChannel]] = io.outputVirtualChannelVec(vcIndex(index))
    val opcode:      Int                                  = opcodeFromLSU(index)
    pcVec.zipWithIndex.foreach { case (pc, li) =>
      val vc = outputVCVec(li)
      vc.valid         := pc.valid
      pc.ready         := vc.ready
      require(pc.bits.getWidth <= parameter.dataWidth, "channel width error.")
      vc.bits.data     := pc.bits.asUInt
      vc.bits.opcode   := opcode.U
      vc.bits.sourceID := parameter.laneNumber.U
      vc.bits.sinkID   := li.U
      vc.bits.last     := false.B
    }
  }

  val physicalChannelToLSU: Seq[
    Vec[_ >: DecoupledIO[UInt] with DecoupledIO[MaskUnitExeReq] with DecoupledIO[V0Update] <: DecoupledIO[Data]]
  ] = Seq(io.readVrfAck, io.maskUnitRequest, io.v0Update, io.lsuWriteAck)
  val opcodeToLSU: Seq[Int] = Seq(1, 3, 5, 10)
  physicalChannelToLSU.zipWithIndex.foreach { case (pcVec, index) =>
    val inputVCVec: Vec[DecoupledIO[LaneVirtualChannel]] = io.inputVirtualChannelVec(index)
    val opcode:     Int                                  = opcodeToLSU(index)
    pcVec.zipWithIndex.foreach { case (pc, li) =>
      val vc = inputVCVec(li)
      pc.valid := vc.valid
      vc.ready := pc.ready
      pc.bits  := vc.bits.data(pc.bits.getWidth - 1, 0).asTypeOf(pc.bits)
//      when(vc.fire) {
//        assert(vc.bits.sinkID === parameter.laneNumber.U)
//        assert(vc.bits.opcode === opcode.U)
//      }
    }
  }

  // lsu <-> sequencer
  val topFromLSU       = Seq(io.lsuReportToTop)
  val opcodeTopFromLSU = Seq(5)
  topFromLSU.zipWithIndex.foreach { case (pc, index) =>
    val vc = io.topOutputVC(index)
    val opcode: Int = opcodeTopFromLSU(index)

    vc.valid         := pc.valid
    pc.ready         := vc.ready
    require(pc.bits.getWidth <= parameter.dataWidth, "channel width error.")
    vc.bits.data     := pc.bits.asUInt
    vc.bits.opcode   := opcode.U
    vc.bits.sourceID := parameter.laneNumber.U
    vc.bits.sinkID   := (parameter.laneNumber + 1).U
    vc.bits.last     := false.B
  }

  val topToLSU       = Seq(io.lsuRequest)
  val opcodeTopToLSU = Seq(0)
  topToLSU.zipWithIndex.foreach { case (pc, index) =>
    val vc = io.topInputVC(index)
    val opcode: Int = opcodeTopToLSU(index)
    pc.valid := vc.valid
    vc.ready := pc.ready
    pc.bits  := vc.bits.data(pc.bits.getWidth - 1, 0).asTypeOf(pc.bits)
//    when(vc.fire) {
//      assert(vc.bits.sinkID === parameter.laneNumber.U)
//      assert(vc.bits.opcode === opcode.U)
//    }
  }

  // Process opcode5 separately
  io.dataInWriteQueue.zip(io.outputVirtualChannelVec(1)).zipWithIndex.foreach { case ((inQ, vc), index) =>
    val instWaitForReport = RegInit(0.U(parameter.chaining1HBits.W))
    val lsuReport         = io.lsuReport
    val reportToLane      = Wire(UInt(parameter.chaining1HBits.W))
    val inShifter         = dataInShifterVec(index)
    when(lsuReport.orR || reportToLane.orR) {
      instWaitForReport := (instWaitForReport | lsuReport) & (~reportToLane).asUInt
    }
    reportToLane := instWaitForReport & (~(inQ | inShifter)).asUInt
    vc.valid         := reportToLane.orR
    vc.bits.data     := reportToLane
    vc.bits.opcode   := 5.U
    vc.bits.sourceID := parameter.laneNumber.U
    vc.bits.sinkID   := index.U
    vc.bits.last     := true.B
  }
}
