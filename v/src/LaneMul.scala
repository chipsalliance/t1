// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package v

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
object LaneMulParam {
  implicit def rw: upickle.default.ReadWriter[LaneMulParam] = upickle.default.macroRW
}
/** @param dataPathWidth width of data path, can be 32 or 64, decides the memory bandwidth. */
case class LaneMulParam(datapathWidth: Int) extends VFUParameter with SerializableModuleParameter {
  val respWidth:   Int = datapathWidth
  val sourceWidth: Int = datapathWidth + 1
  val decodeField: BoolField = Decoder.multiplier
  val inputBundle = new LaneMulReq(this)
  val outputBundle = new LaneMulResponse(this)
}

class LaneMulReq(parameter: LaneMulParam) extends Bundle {
  val src:      Vec[UInt] = Vec(3, UInt(parameter.sourceWidth.W))
  val opcode:   UInt = UInt(4.W)
  val saturate: Bool = Bool()
  val vSew: UInt = UInt(2.W)

  /** Rounding mode register */
  val vxrm: UInt = UInt(2.W)
}

class LaneMulResponse(parameter: LaneMulParam) extends Bundle {
  val data: UInt = UInt(parameter.respWidth.W)
  val vxsat: Bool = Bool()
}

class LaneMul(val parameter: LaneMulParam) extends VFUModule(parameter) with SerializableModule[LaneMulParam] {
  val response: LaneMulResponse = Wire(new LaneMulResponse(parameter))
  val request: LaneMulReq = connectIO(response).asTypeOf(parameter.inputBundle)

  val sew1H: UInt = UIntToOH(request.vSew)(2, 0)
  val vSewOrR = request.vSew.orR
  val vxrm1H: UInt = UIntToOH(request.vxrm)
  // ["mul", "ma", "ms", "mh"]
  val opcode1H: UInt = UIntToOH(request.opcode(1, 0))
  val ma:       Bool = opcode1H(1) || opcode1H(2)
  val asAddend = request.opcode(2)
  val negative = request.opcode(3)

  // vs1 一定是被乘数
  val mul0: UInt = request.src.head
  // 另一个乘数
  val mul1: UInt = Mux(asAddend || !ma, request.src(1), request.src.last)
  // 加数
  val addend: UInt = Mux(asAddend, request.src.last, request.src(1))
  // 乘的结果 todo: csa & delete SInt
  val mulResult: UInt = (mul0.asSInt * mul1.asSInt).asUInt
  // 处理 saturate
  /** clip(roundoff_signed(vs2[i]*vs1[i], SEW-1))
    * v[d-1]
    * v[d-1] & (v[d-2:0]≠0 | v[d])
    * 0
    * !v[d] & v[d-1:0]≠0
    */
  val vd1:        Bool = Mux1H(sew1H, Seq(mulResult(6), mulResult(14), mulResult(30)))
  val vd:         Bool = Mux1H(sew1H, Seq(mulResult(7), mulResult(15), mulResult(31)))
  val vd2OR:      Bool = Mux1H(sew1H, Seq(mulResult(5, 0).orR, mulResult(13, 0).orR, mulResult(29, 0).orR))
  val roundBits0: Bool = vd1
  val roundBits1: Bool = vd1 && (vd2OR || vd)
  val roundBits2: Bool = !vd && (vd2OR || vd1)
  val roundBits:  Bool = Mux1H(vxrm1H(3) ## vxrm1H(1, 0), Seq(roundBits0, roundBits1, roundBits2))
  // 去掉低位
  val shift0 = Mux(sew1H(0), mulResult >> 7, mulResult)
  val shift1 = Mux(sew1H(1), shift0 >> 15, shift0)
  val shift2 = Mux(sew1H(2), shift1 >> 31, shift1).asUInt
  val highResult = (shift2 >> 1).asUInt
  val saturateResult = (shift2 + roundBits)(parameter.datapathWidth, 0)

  /** lower: 下溢出近值
    * upper: 上溢出近值
    */
  val lower: UInt = request.vSew(1) ## 0.U(15.W) ## request.vSew(0) ## 0.U(7.W) ## !vSewOrR ## 0.U(7.W)
  val upper: UInt = Fill(16, request.vSew(1)) ## Fill(8, vSewOrR) ## Fill(7, true.B)
  val sign0 = Mux1H(sew1H, Seq(request.src.head(7), request.src.head(15), request.src.head(31)))
  val sign1 = Mux1H(sew1H, Seq(request.src(1)(7), request.src(1)(15), request.src(1)(31)))
  val sign2 = Mux1H(sew1H, Seq(saturateResult(7), saturateResult(15), saturateResult(31)))
  val notZero = Mux1H(sew1H, Seq(saturateResult(7, 0).orR, saturateResult(15, 0).orR, saturateResult(31, 0).orR))
  val expectedSig = sign0 ^ sign1
  val overflow = (expectedSig ^ sign2) && notZero
  val overflowSelection = Mux(expectedSig, lower, upper)
  // 反的乘结果
  val negativeResult: UInt = (~mulResult).asUInt
  // 选乘的结果
  val adderInput0: UInt = Mux(negative, negativeResult, mulResult)
  // 加法
  val maResult: UInt = adderInput0 + addend + negative
  // 选最终的结果 todo: decode
  response.data := Mux1H(
    Seq(opcode1H(0) && !request.saturate, opcode1H(3), ma, request.saturate && !overflow, request.saturate && overflow),
    Seq(mulResult, highResult, maResult, saturateResult, overflowSelection)
  )
  // todo
  response.vxsat := DontCare
}
