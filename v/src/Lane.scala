package v

import chisel3._
import chisel3.util._

class LaneReq(param: VectorParameters) extends Bundle {
  val uop: UInt = UInt(4.W)
}

class LaneResp(param: VectorParameters) extends Bundle {
  val mask: UInt = UInt(4.W)
}

class Lane(param: VectorParameters) extends Module {
  val req: DecoupledIO[LaneReq] = IO(Flipped(Decoupled(new LaneReq(param))))
  val resp: ValidIO[LaneResp] = IO(Valid(new LaneResp(param)))
}
