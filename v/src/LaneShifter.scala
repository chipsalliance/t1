package v

import chisel3._
import chisel3.util._

case class LaneShifterParameter(dataWidth: Int, shifterSizeBit: Int)

class LaneShifterReq(param: LaneShifterParameter) extends Bundle {
  val src:         UInt = UInt(param.dataWidth.W)
  val shifterSize: UInt = UInt(param.shifterSizeBit.W)
  val opcode:      UInt = UInt(2.W)
}

class LaneShifter(param: LaneShifterParameter) extends Module {
  val req:  LaneShifterReq = IO(Input(new LaneShifterReq(param)))
  val resp: UInt = IO(Output(UInt(param.dataWidth.W)))

  // arithmetic
  val extend:     UInt = Fill(param.dataWidth, req.opcode(1))
  val extendData: UInt = extend ## req.src

  resp := Mux(req.opcode(0), extendData << req.shifterSize, extendData >> req.shifterSize)
}
