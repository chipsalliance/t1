package v

import chisel3._
import chisel3.util._

case class VParam(ELEN: Int = 32) {
  def laneParam: LaneParameters = LaneParameters(ELEN)
}

class VReq(param: VParam) extends Bundle {
  val inst:     UInt = UInt(32.W)
  val src1Data: UInt = UInt(param.ELEN.W)
  val src2Data: UInt = UInt(param.ELEN.W)
}

class VResp(param: VParam) extends Bundle {
  val data: UInt = UInt(param.ELEN.W)
}

class V(param: VParam) extends Module {
  val req:              DecoupledIO[VReq] = IO(Flipped(Decoupled(new VReq(param))))
  val resp:             ValidIO[VResp] = IO(Valid(new VResp(param)))
  val csrInterface:     LaneCsrInterface = IO(Input(new LaneCsrInterface(param.laneParam.VLMaxBits)))
  val storeBufferClear: Bool = IO(Input(Bool()))
  // 后续需要l2的 tile link 接口
}
