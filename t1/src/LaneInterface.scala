// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.interface

import chisel3._
import chisel3.experimental.hierarchy._
import chisel3.experimental._
import chisel3.ltl._
import chisel3.ltl.Sequence._
import chisel3.probe.{Probe, ProbeValue, define}
import chisel3.properties.{AnyClassType, ClassType, Path, Property}
import chisel3.util._
import chisel3.util.experimental.decode.DecodeBundle
import org.chipsalliance.t1.rtl.decoder.{Decoder, DecoderParam}
import org.chipsalliance.t1.rtl.lane._
import org.chipsalliance.t1.rtl.vrf.{RamType, VRF, VRFParam, VRFProbe}
import org.chipsalliance.dwbb.stdlib.queue.{Queue, QueueIO}
import org.chipsalliance.stdlib.GeneralOM
import org.chipsalliance.t1.rtl.{CSRInterface, LaneParameter, LaneTokenBundle, MaskUnitExeReq, V0Update, VRFReadRequest, VRFWriteRequest}

class LaneRequest(parameter: LaneParameter) extends Bundle {
  val instructionIndex: UInt         = UInt(parameter.instructionIndexBits.W)
  // decode
  val decodeResult:     DecodeBundle = Decoder.bundle(parameter.decoderParam)
  val loadStore:        Bool         = Bool()
  val issueInst:        Bool         = Bool()
  val store:            Bool         = Bool()
  val special:          Bool         = Bool()
  val lsWholeReg:       Bool         = Bool()

  // instruction
  /** vs1 or imm */
  val vs1: UInt = UInt(5.W)

  /** vs2 or rs2 */
  val vs2: UInt = UInt(5.W)

  /** vd or rd */
  val vd: UInt = UInt(5.W)

  val loadStoreEEW: UInt = UInt(2.W)

  /** mask type ? */
  val mask: Bool = Bool()

  val segment: UInt = UInt(3.W)

  /** data of rs1 */
  val readFromScalar: UInt = UInt(parameter.datapathWidth.W)

  val csrInterface: CSRInterface = new CSRInterface(parameter.vlMaxBits)

  val writeCount: UInt = UInt((parameter.vlMaxBits - log2Ceil(parameter.laneNumber) - log2Ceil(parameter.dataPathByteWidth)).W)

  // vmacc 的vd需要跨lane读 TODO: move to [[V]]
  def ma: Bool =
    decodeResult(Decoder.multiplier) && decodeResult(Decoder.uop)(1, 0).xorR && !decodeResult(Decoder.vwmacc)
}

class MaskRequestAck(parameter: LaneParameter) extends Bundle {
  val data: UInt = UInt(parameter.maskGroupWidth.W)
}

class LSUReport(parameter: LaneParameter) extends Bundle {
  val last: UInt = UInt(parameter.chaining1HBits.W)
}

class VrfWrite(parameter: LaneParameter) extends Bundle {
  val writeRequest: VRFWriteRequest = new VRFWriteRequest(
    parameter.vrfParam.regNumBits,
    parameter.vrfOffsetBits,
    parameter.instructionIndexBits,
    parameter.datapathWidth
  )

  val fromMask: Bool = Bool()
}

class ReadBusData(param: LaneParameter) extends Bundle {

  /** data field of the bus. */
  val data: UInt = UInt(param.datapathWidth.W)
}

class WriteBusData(param: LaneParameter) extends Bundle {

  /** data field of the bus. */
  val data: UInt = UInt(param.datapathWidth.W)

  /** used for instruction with mask. */
  val mask: UInt = UInt((param.datapathWidth / 2 / 8).W)

  /** which instruction is the source of this transaction. */
  val instructionIndex: UInt = UInt(param.instructionIndexBits.W)

  /** define the order of the data to dequeue from ring. */
  val counter: UInt = UInt(param.groupNumberBits.W)
}

class MaskRequest(parameter: LaneParameter) extends Bundle {
  /** select which mask group. */
  val maskSelect: UInt = UInt(parameter.maskGroupSizeBits.W)

  /** The sew of instruction which is requesting for mask. */
  val maskSelectSew: UInt = UInt(2.W)
}

class LaneResponse(parameter: LaneParameter) extends Bundle {
  val instructionFinished: UInt = UInt(parameter.chaining1HBits.W)
  val vxsatReport:         UInt = UInt(parameter.chaining1HBits.W)
  // todo
  val writeQueueValid:  UInt = UInt(parameter.chaining1HBits.W)
}

class MaskUnitRequest(parameter: LaneParameter) extends Bundle {
  val request = new MaskUnitExeReq(parameter)
  val toLSU: Bool = Bool()
}

class LaneInterfaceRelease extends Bundle {
  val input: Vec[Bool] = Input(Vec(7, Bool()))
  val output: Vec[Bool] = Output(Vec(7, Bool()))
}

class LaneVirtualChannel(parameter: LaneParameter) extends Bundle {
  // todo: add param for LaneInterface
  val dataWidth: Int = new LaneRequest(parameter).getWidth
  // todo
  val lsuSize = 1
  // lane + lsu + top + mask unit
  val idWidth: Int = log2Ceil(parameter.laneNumber + lsuSize + 1 + 1)
  // todo
  val opcodeWidth: Int = log2Ceil(7)

  val data: UInt = UInt(dataWidth.W)

  val opcode: UInt = UInt(opcodeWidth.W)

  val sourceID: UInt = UInt(idWidth.W)
  val sinkID: UInt = UInt(idWidth.W)

  // todo
  val last: Bool = Bool()
}


class LaneInterfaceIO(parameter: LaneParameter) extends Bundle {
  val clock: Clock = Input(Clock())
  val reset: Reset = Input(Reset())

  // lane input => interface output
  // opcode 0
  val laneRequest: DecoupledIO[LaneRequest] = Decoupled(new LaneRequest(parameter))

  // opcode 1
  val vrfReadRequest: DecoupledIO[VRFReadRequest] = Decoupled(
    new VRFReadRequest(parameter.vrfParam.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits)
  )

  // opcode 2
  val maskRequestAck: DecoupledIO[MaskRequestAck] = Decoupled(new MaskRequestAck(parameter))

  // opcode 3
  // todo: Multiple copies?
  val readBusEnq: DecoupledIO[ReadBusData] = Decoupled(new ReadBusData(parameter))

  // opcode 4
  val writeBusEnq: DecoupledIO[WriteBusData] = Decoupled(new WriteBusData(parameter))

  // opcode 5
  val lsuReport: DecoupledIO[LSUReport] = Decoupled(new LSUReport(parameter))

  // opcode 6
  val vrfWriteRequest: DecoupledIO[VrfWrite] = Decoupled(new VrfWrite(parameter))

  // lane output => interface input
  // opcode 0
  val maskRequest: DecoupledIO[MaskRequest] = Flipped(Decoupled(new MaskRequest(parameter)))

  // opcode 1
  val readVrfAck: DecoupledIO[UInt] = Flipped(Decoupled(UInt(parameter.datapathWidth.W)))

  // opcode 2
  val readBusDeq: DecoupledIO[ReadBusData] = Flipped(Decoupled(new ReadBusData(parameter)))

  // opcode 3
  val maskUnitRequest: DecoupledIO[MaskUnitRequest] = Flipped(Decoupled(new MaskUnitRequest(parameter)))

  // opcode 4
  val writeBusDeq: DecoupledIO[WriteBusData] = Flipped(Decoupled(new WriteBusData(parameter)))

  // opcode 5
  val v0Update: DecoupledIO[V0Update] = Flipped(Decoupled(new V0Update(parameter.datapathWidth, parameter.vrfOffsetBits)))

  // opcode 6
  val laneResponse: DecoupledIO[LaneResponse] = Flipped(Decoupled(new LaneResponse(parameter)))

  val release = new LaneInterfaceRelease

  val inputVirtualChannelVec: Vec[ValidIO[LaneVirtualChannel]] = Vec(7, Flipped(Valid(new LaneVirtualChannel(parameter))))
  val outputVirtualChannelVec: Vec[ValidIO[LaneVirtualChannel]] = Vec(7, Valid(new LaneVirtualChannel(parameter)))
}

class LaneInterface (val parameter: LaneParameter)
  extends FixedIORawModule(new LaneInterfaceIO(parameter))
    with SerializableModule[LaneParameter]
    with ImplicitClock
    with ImplicitReset {

  protected def implicitClock = io.clock
  protected def implicitReset = io.reset

}
