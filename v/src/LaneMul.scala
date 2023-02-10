package v

import chisel3._
import chisel3.util._

case class LaneMulParam(inputWidth: Int, vlWidth: Int) {
  val respWidth: Int = inputWidth
}

class LaneMulReq(parameter: LaneMulParam) extends Bundle {
  val src:    Vec[UInt] = Vec(3, UInt(parameter.inputWidth.W))
  val opcode: UInt = UInt(4.W)
  val saturate: Bool = Bool()
}

class LaneMul(parameter: LaneMulParam) extends Module {
  val req:  LaneMulReq = IO(Input(new LaneMulReq(parameter)))
  val resp: UInt = IO(Output(UInt(parameter.respWidth.W)))
  val vxsat: Bool = IO(Output(Bool()))
  val csrInterface: LaneCsrInterface = IO(Input(new LaneCsrInterface(parameter.vlWidth)))

  val sew1H: UInt = UIntToOH(csrInterface.vSew)(2, 0)
  val vxrm1H: UInt = UIntToOH(csrInterface.vxrm)
  // ["mul", "ma", "ms", "mh"]
  val opcode1H: UInt = UIntToOH(req.opcode(1,0))
  val ma: Bool = opcode1H(1) || opcode1H(2)
  val asAddend = req.opcode(2)
  val negative = req.opcode(3)

  // vs1 一定是被乘数
  val mul0: UInt = req.src.head
  // 另一个乘数
  val mul1: UInt = Mux(asAddend || !ma, req.src(1), req.src.last)
  // 加数
  val addend: UInt = Mux(asAddend, req.src.last, req.src(1))
  // 乘的结果
  val mulResult: UInt = mul0 * mul1
  // 处理 saturate
  /** clip(roundoff_signed(vs2[i]*vs1[i], SEW-1))
    * v[d-1]
    * v[d-1] & (v[d-2:0]≠0 | v[d])
    * 0
    * !v[d] & v[d-1:0]≠0
    */
  val vd1: Bool = Mux1H(sew1H, Seq(mulResult(6), mulResult(14), mulResult(30)))
  val vd: Bool = Mux1H(sew1H, Seq(mulResult(7), mulResult(15), mulResult(31)))
  val vd2OR: Bool = Mux1H(sew1H, Seq(mulResult(5, 0).orR, mulResult(13, 0).orR, mulResult(29, 0).orR))
  val roundBits0: Bool = vd1
  val roundBits1: Bool = vd1 && (vd2OR || vd)
  val roundBits2: Bool = !vd && (vd2OR || vd1)
  val roundBits: Bool = Mux1H(vxrm1H(3) ## vxrm1H(1, 0), Seq(roundBits0, roundBits1, roundBits2))
  // 去掉低位
  val shift0 = Mux(sew1H(0), mulResult >> 7, mulResult)
  val shift1 = Mux(sew1H(1), shift0 >> 15, shift0)
  val shift2 = Mux(sew1H(2), shift1 >> 31, shift1).asUInt
  val highResult = (shift2 >> 1).asUInt
  val saturateResult = shift2 + roundBits
  // 反的乘结果
  val negativeResult: UInt = (~mulResult).asUInt
  // 选乘的结果
  val adderInput0: UInt = Mux(negative, negativeResult, mulResult)
  // 加法
  val maResult: UInt = adderInput0 + addend + negative
  // 选最终的结果 todo: decode
  resp := Mux1H(
    Seq(opcode1H(0) && !req.saturate, opcode1H(3), ma, req.saturate),
    Seq(mulResult, highResult, maResult, saturateResult)
  )
  // todo
  vxsat := DontCare
}
