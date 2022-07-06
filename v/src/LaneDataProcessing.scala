package v

import chisel3._
import chisel3.util._

case class LaneDataProcessingParameter(dataWidth: Int, maskWidth: Int, shifterSizeBit: Int, roundSizeBit: Int)

class LaneDPReq(param: LaneDataProcessingParameter) extends Bundle {
  val src:   UInt = UInt(param.dataWidth.W)
  val sign:  Bool = Bool()
  val mask:  UInt = UInt(param.maskWidth.W)
  val rm:    UInt = UInt(2.W)
  val rSize: ValidIO[UInt] = Valid(UInt(param.roundSizeBit.W))
}

class LaneDataProcessing(param: LaneDataProcessingParameter) extends Module {
  val in:   LaneDPReq = IO(Input(new LaneDPReq(param)))
  val resp: UInt = IO(Output(UInt(param.dataWidth.W)))
  val clipCheckFail: Bool = IO(Output(Bool()))

  val signValue: Bool = in.src(param.dataWidth - 1) && in.sign
  val signExtend: UInt = Fill(param.dataWidth, signValue)

  // remainder tail
  val roundTail: UInt = (1.U << in.rSize.bits).asUInt
  val lostMSB: UInt = (roundTail >> 1).asUInt
  val roundMask: UInt = roundTail - 1.U

  // v[d - 1]
  val vds1: Bool = (lostMSB & in.src).orR
  // v[d -2 : 0]
  val vLostLSB: Bool = (roundMask & in.src).orR
  // v[d]
  val vd: Bool = (roundTail & in.src).orR
  // r
  val roundR: Bool = Mux1H(UIntToOH(in.rm), Seq(vds1, vds1 & (vLostLSB | vd), false.B, !vd & (vds1 | vLostLSB)))
  val roundResult: UInt = (((signExtend ## in.src) >> in.rSize.bits).asUInt + roundR)(param.dataWidth - 1, 0)
  resp := Mux(in.rSize.valid, roundResult, in.src)
  // todo
  clipCheckFail := true.B
}
