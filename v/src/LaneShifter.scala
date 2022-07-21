package v

import chisel3._
import chisel3.util._

case class LaneShifterParameter(dataWidth: Int, shifterSizeBit: Int)

class LaneShifter(param: LaneShifterParameter) extends Module {
  val src: UInt = IO(Input(UInt(param.dataWidth.W)))
  val sign: Bool = IO(Input(Bool()))
  val direction: Bool = IO(Input(Bool()))
  // Todo: need cut for difference sew
  val shifterSize: UInt = IO(Input(UInt(param.shifterSizeBit.W)))
  // mask for sew
  val mask: UInt = IO(Input(UInt(param.dataWidth.W)))
  val resp: UInt = IO(Output(UInt(param.dataWidth.W)))

  val sign1h: UInt = mask & (~mask >> 1).asUInt
  val signValue: Bool = (sign1h & src).orR
  val signExtend: UInt = Fill(param.dataWidth, signValue)
  val dataMask: UInt = (src & mask) | (signExtend & (~mask).asUInt)
  val extendData: UInt = signExtend ## dataMask

  resp := Mux(direction, extendData >> shifterSize, extendData << shifterSize)
}