package v

import chisel3._
import chisel3.util._
import tilelink.{TLBundle, TLBundleParameter, TLChannelAParameter, TLChannelDParameter}

case class VParam(ELEN: Int = 32, VLEN: Int = 1024, lane: Int = 8, vaWidth: Int = 32) {
  val tlBank:         Int = 2
  val sourceWidth:    Int = 10
  val maskGroupWidth: Int = 32
  val maskGroupSize:  Int = VLEN / 32
  val chainingSize:   Int = 4
  def laneParam:      LaneParameters = LaneParameters(ELEN)
  val tlParam: TLBundleParameter = TLBundleParameter(
    a = TLChannelAParameter(vaWidth, sourceWidth, ELEN, 2, 4),
    b = None,
    c = None,
    d = TLChannelDParameter(sourceWidth, sourceWidth, ELEN, 2),
    e = None
  )
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
  val tlPort: Vec[TLBundle] = IO(Vec(param.tlBank, param.tlParam.bundle()))
}
