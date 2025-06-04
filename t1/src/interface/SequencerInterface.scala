// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental._
import chisel3.util._
import org.chipsalliance.t1.rtl.decoder.DecoderParam


case class SequencerIFParam(
                            vLen:                             Int,
                            eLen:                             Int,
                            datapathWidth:                    Int,
                            laneNumber:                       Int,
                            chainingSize:                     Int,
                            fpuEnable:                        Boolean,
                            decoderParam:                     DecoderParam)
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
  val regNumBits:           Int = log2Ceil(regNum)

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
  val idWidth: Int = log2Ceil(laneNumber + lsuSize + 1 + 1)
  // todo
  val opcodeWidth: Int = log2Ceil(7)
}

class SequencerInterfaceIO(parameter: SequencerIFParam) extends Bundle {
  val clock: Clock = Input(Clock())
  val reset: Reset = Input(Reset())

  // opcode 0
  val laneRequest: Vec[DecoupledIO[LaneRequest]] = Vec(parameter.laneNumber, Flipped(Decoupled(new LaneRequest(
    parameter.instructionIndexBits,
    parameter.decoderParam,
    parameter.datapathWidth,
    parameter.vlMaxBits,
    parameter.laneNumber,
    parameter.dataPathByteWidth
  ))))

  // opcode 1
  val vrfReadRequest: Vec[DecoupledIO[VRFReadRequest]] = Vec(parameter.laneNumber, Flipped(Decoupled(
    new VRFReadRequest(parameter.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits)
  )))

  // opcode 2
  val maskRequestAck: Vec[DecoupledIO[MaskRequestAck]] = Vec(parameter.laneNumber, Flipped(Decoupled(new MaskRequestAck(parameter.maskGroupWidth))))

  // opcode 6
  val vrfWriteRequest: Vec[DecoupledIO[VrfWrite]] = Vec(parameter.laneNumber, Flipped(Decoupled(new VrfWrite(
    parameter.regNumBits,
    parameter.vrfOffsetBits,
    parameter.instructionIndexBits,
    parameter.datapathWidth
  ))))

  // interface => sequencer
  // opcode 0
  val maskRequest = Vec(parameter.laneNumber, Decoupled(new MaskRequest(parameter.maskGroupSizeBits)))

  // opcode 1
  val readVrfAck = Vec(parameter.laneNumber, Decoupled(UInt(parameter.datapathWidth.W)))

  // opcode 3
  val maskUnitRequest = Vec(parameter.laneNumber, Decoupled(new MaskUnitRequest(parameter.eLen, parameter.datapathWidth, parameter.instructionIndexBits, parameter.fpuEnable)))

  // opcode 5
  val v0Update = Vec(parameter.laneNumber, Decoupled(new V0Update(parameter.datapathWidth, parameter.vrfOffsetBits)))

  // opcode 6
  val laneResponse = Vec(parameter.laneNumber, Decoupled(new LaneResponse(parameter.chaining1HBits)))

  val inputVirtualChannelVec: Vec[Vec[DecoupledIO[LaneVirtualChannel]]] = Vec(5, Vec(parameter.laneNumber,
    Flipped(Decoupled(new LaneVirtualChannel(parameter.dataWidth, parameter.opcodeWidth, parameter.idWidth)))
  ))
  val outputVirtualChannelVec: Vec[Vec[DecoupledIO[LaneVirtualChannel]]] = Vec(4, Vec(parameter.laneNumber,
    Decoupled(new LaneVirtualChannel(parameter.dataWidth, parameter.opcodeWidth, parameter.idWidth))
  ))

  // Seq <-> lsu

  // opcode 0
  val lsuRequest: DecoupledIO[LSURequestInterface] = Flipped(Decoupled(new LSURequestInterface(parameter.datapathWidthBits, parameter.chainingSize, parameter.vlMaxBits)))

  // opcode 5
  val lsuReportToTop: DecoupledIO[LSUReport] = Decoupled(new LSUReport(parameter.chaining1HBits))

  val topInputVC: Vec[DecoupledIO[LaneVirtualChannel]] = Vec(1, Flipped(Decoupled(new LaneVirtualChannel(parameter.dataWidth, parameter.opcodeWidth, parameter.idWidth))))
  val topOutputVC: Vec[DecoupledIO[LaneVirtualChannel]] = Vec(1, Decoupled(new LaneVirtualChannel(parameter.dataWidth, parameter.opcodeWidth, parameter.idWidth)))
}

class SequencerInterface (val parameter: SequencerIFParam)
  extends FixedIORawModule(new SequencerInterfaceIO(parameter))
    with SerializableModule[SequencerIFParam]
    with ImplicitClock
    with ImplicitReset {

  protected def implicitClock = io.clock
  protected def implicitReset = io.reset


  val physicalChannelFromSequencer = Seq(io.laneRequest, io.vrfReadRequest, io.maskRequestAck, io.vrfWriteRequest)
  val opcodeFromSequencer: Seq[Int] = Seq(0, 1, 2, 6)

  physicalChannelFromSequencer.zipWithIndex.foreach { case (pcVec, index) =>
    val outputVCVec: Vec[DecoupledIO[LaneVirtualChannel]] = io.outputVirtualChannelVec(index)
    val opcode: Int = opcodeFromSequencer(index)
    pcVec.zipWithIndex.foreach { case (pc, li) =>
      val vc = outputVCVec(li)
      vc.valid := pc.valid
      pc.ready := vc.ready
      require(pc.bits.getWidth <= parameter.dataWidth, "channel width error.")
      vc.bits.data := pc.bits.asUInt
      vc.bits.opcode := opcode.U
      vc.bits.sourceID := (parameter.laneNumber + 1).U
      vc.bits.sinkID := li.U
      vc.bits.last := false.B
    }
  }

  val physicalChannelToSequencer = Seq(io.maskRequest, io.readVrfAck, io.maskUnitRequest, io.v0Update, io.laneResponse)
  val opcodeToSequencer: Seq[Int] = Seq(0, 1, 3, 5, 6)
  physicalChannelToSequencer.zipWithIndex.foreach { case (pcVec, index) =>
    val inputVCVec: Vec[DecoupledIO[LaneVirtualChannel]] = io.inputVirtualChannelVec(index)
    val opcode: Int = opcodeToSequencer(index)
    pcVec.zipWithIndex.foreach { case (pc, li) =>
      val vc = inputVCVec(li)
      pc.valid := vc.valid
      vc.ready := pc.ready
      pc.bits := vc.bits.data(pc.bits.getWidth - 1, 0).asTypeOf(pc.bits)
      when(vc.fire) {
        assert(vc.bits.sinkID === (parameter.laneNumber + 1).U)
        assert(vc.bits.opcode === opcode.U)
      }
    }
  }

  // lsu <-> sequencer
  val topToLSU = Seq(io.lsuRequest)
  val opcodeTopToLSU = Seq(0)
  topToLSU.zipWithIndex.foreach {case (pc, index) =>
    val vc = io.topInputVC(index)
    val opcode: Int = opcodeTopToLSU(index)

    vc.valid := pc.valid
    pc.ready := vc.ready
    require(pc.bits.getWidth <= parameter.dataWidth, "channel width error.")
    vc.bits.data := pc.bits.asUInt
    vc.bits.opcode := opcode.U
    vc.bits.sourceID := parameter.laneNumber.U
    vc.bits.sinkID := (parameter.laneNumber + 1).U
    vc.bits.last := false.B
  }

  val topFromLSU = Seq(io.lsuReportToTop)
  val opcodeTopFromLSU = Seq(5)
  topFromLSU.zipWithIndex.foreach { case (pc, index) =>
    val vc = io.topOutputVC(index)
    val opcode: Int = opcodeTopFromLSU(index)
    pc.valid := vc.valid
    vc.ready := pc.ready
    pc.bits := vc.bits.data(pc.bits.getWidth - 1, 0).asTypeOf(pc.bits)
    when(vc.fire) {
      assert(vc.bits.sinkID === parameter.laneNumber.U)
      assert(vc.bits.opcode === opcode.U)
    }
  }
}
