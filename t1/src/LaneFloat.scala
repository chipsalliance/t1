// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3.experimental.hierarchy.instantiable
import chisel3.{UInt, _}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
import chisel3.ltl._
import chisel3.ltl.Sequence._
import hardfloat._
import org.chipsalliance.t1.rtl.decoder.{BoolField, Decoder}

object LaneFloatParam {
  implicit def rw: upickle.default.ReadWriter[LaneFloatParam] = upickle.default.macroRW
}

case class LaneFloatParam(datapathWidth: Int, latency: Int) extends VFUParameter with SerializableModuleParameter {
  val decodeField: BoolField = Decoder.float
  val inputBundle = new LaneFloatRequest(datapathWidth)
  val outputBundle = new LaneFloatResponse(datapathWidth)
  override val NeedSplit: Boolean = false
}

/** UOP encoding
  *
  * for fmaEn
  * up[3] = 1 for add/sub
  * up[2] = 1 for reverse in rsub and reversed fmaEn
  * up[1:0] for fmaEn sign, 0 for positive, 1 for negative
  *
  * For compareModule
  * 0001 EQ
  * 0000 NQ
  * 0010 LT
  * 0011 LE
  * 0100 GT
  * 0101 GE
  *
  * 1000 min
  * 1100 max
  *
  *
  * none of these 3
  *
  * 0001 SGNJ
  * 0010 SGNJN
  * 0011 SGNJX
  * 0100 classify
  * 0101 merge
  * 0110 sqrt7
  * 0111 rec7
  *
  * 1000 to float
  * 1010 to sint
  * 1001 to uint
  * 1110 to sint tr
  * 1101 to uint tr
  *
  */
class LaneFloatRequest(datapathWidth: Int) extends VFUPipeBundle {
  val sign = Bool()
  val src   = Vec(3, UInt(datapathWidth.W))
  val opcode = UInt(4.W)
  val unitSelet = UInt(2.W)
  val floatMul = Bool()
  val roundingMode = UInt(3.W)
  val executeIndex: UInt = UInt(2.W)
}

class LaneFloatResponse(datapathWidth: Int)  extends VFUPipeBundle {
  val data = UInt(datapathWidth.W)
  val adderMaskResp: Bool = Bool()
  val exceptionFlags = UInt(5.W)
  val executeIndex: UInt = UInt(2.W)
}

@instantiable
class LaneFloat(val parameter: LaneFloatParam) extends VFUModule(parameter) with SerializableModule[LaneFloatParam]{
  val response: LaneFloatResponse = Wire(new LaneFloatResponse(parameter.datapathWidth))
  val request : LaneFloatRequest  = connectIO(response, true.B).asTypeOf(parameter.inputBundle)

  val recIn0 = recFNFromFN(8, 24, request.src(0))
  val recIn1 = recFNFromFN(8, 24, request.src(1))
  val recIn2 = recFNFromFN(8, 24, request.src(2))
  val raw0   = rawFloatFromRecFN(8, 24, recIn0)
  val raw1   = rawFloatFromRecFN(8, 24, recIn1)

  val uop = request.opcode

  val unitSeleOH = UIntToOH(request.unitSelet)
  val fmaEn     = unitSeleOH(0)
  val compareEn = unitSeleOH(2)
  val otherEn   = unitSeleOH(3)

  val result = Wire(UInt(32.W))
  val flags  = Wire(UInt(5.W))

  /** Vector Single-Width Floating-Point Add/Subtract/Multiply/Floating-Point Fused Multiply-Add Instructions
    *
    * encoding
    * {{{
    *   addsub      sub          mul     maf     rmaf
    * a  src(0)     src(1)      src(0)  src(0)  src(0)
    * b  1               1      src(1)  src(1)  src(2)
    * c  src(1)     src(0)      0       src(2)  src(1)
    * }}}
    */

  /** MAF logic */
  val sub    = fmaEn && uop === 9.U
  val addsub = fmaEn && request.opcode(3)
  val maf    = fmaEn && (uop(3,2) === 0.U)
  val rmaf   = fmaEn && (uop(3,2) === 1.U)

  val mulAddRecFN = Module(new MulAddRecFN(8, 24))
  val fmaIn0 = Mux(sub, recIn1, recIn0)
  val fmaIn1 = Mux(addsub, (BigInt(1)<<(parameter.datapathWidth - 1)).U, Mux(rmaf, recIn2, recIn1))
  val fmaIn2 = Mux(sub, recIn0,
    Mux(maf && !request.floatMul, recIn2,
      Mux(maf && request.floatMul, ((request.src(0) ^ request.src(1)) & (BigInt(1) << (parameter.datapathWidth - 1)).U) << 1, recIn1)))

  mulAddRecFN.io.op := request.opcode(1,0)
  mulAddRecFN.io.a := fmaIn0
  mulAddRecFN.io.b := fmaIn1
  mulAddRecFN.io.c := fmaIn2
  mulAddRecFN.io.roundingMode := request.roundingMode //todo decode it
  mulAddRecFN.io.detectTininess := false.B

  val fmaResult = fNFromRecFN(8, 24, mulAddRecFN.io.out)


  /** CompareModule
    *
    * perform a signaling comparing in IEEE-754 for LE,LT,GT,GE
    *
    * UOP
    * {{{
    * 0001 EQ
    * 0000 NQ
    * 0010 LT
    * 0011 LE
    * 0100 GT
    * 0101 GE
    *
    * 1000 min
    * 1100 max
    * }}}
    *
    *
    */
  val compareModule = Module(new CompareRecFN(8, 24))
  compareModule.io.a := recIn1
  compareModule.io.b := recIn0
  compareModule.io.signaling := uop(3,1) === "b001".U || uop(3,1) === "b010".U
  val compareResult = Wire(UInt(32.W))
  val compareFlags  = Wire(UInt(5.W))
  val oneNaN = raw0.isNaN ^  raw1.isNaN
  val compareNaN = Mux(oneNaN,
    Mux(raw0.isNaN, request.src(1), request.src(0)),
    "x7fc00000".U
  )
  val hasNaN = raw0.isNaN ||  raw1.isNaN
  val differentZeros = compareModule.io.eq && (request.src(1)(31) ^ request.src(0)(31))

  AssertProperty(BoolSequence(!unitSeleOH(2) || (uop === "b0001".U || uop === "b0000".U || uop === "b0010".U || uop === "b0011".U || uop === "b0100".U || uop === "b0101".U || uop === "b1000".U || uop === "b1100".U)))
  compareResult := Mux(uop === BitPat("b1?00") && hasNaN ,compareNaN,
    Mux(uop === BitPat("b1?00"), Mux((!uop(2) && compareModule.io.lt) || (uop(2) && compareModule.io.gt) || (differentZeros && (uop(2) ^ request.src(1)(31))), request.src(1), request.src(0)),
     Mux(uop === "b0011".U, compareModule.io.lt || compareModule.io.eq,
       Mux(uop === "b0101".U, compareModule.io.gt || compareModule.io.eq,
         Mux(uop === "b0010".U, compareModule.io.lt,
           Mux(uop === "b0100".U, compareModule.io.gt,
             Mux(uop === "b0000".U, !compareModule.io.eq,
               compareModule.io.eq)))))))
  compareFlags := compareModule.io.exceptionFlags

  /** Other Unit
    * {{{
    * uop(3,2) =
    * 1x => convert
    * 00 => sgnj
    * 01 => clasify,merge,rec7,sqrt7
    *
    * 0001 SGNJ
    * 0010 SGNJN
    * 0011 SGNJX
    *
    * 0100 classify
    * 0101 merge
    * 0110 rec7
    * 0111 sqrt7
    *
    * 1000 to float
    * 1010 to sint
    * 1001 to uint
    * 1110 to sint tr
    * 1101 to uint tr
    * }}}
    * */
  val intToFn = Module(new INToRecFN(32, 8, 24))
  intToFn.io.in := request.src(1)
  intToFn.io.signedIn := request.sign
  intToFn.io.roundingMode := request.roundingMode
  intToFn.io.detectTininess := false.B

  /** io.signedOut define output sign
    *
    * false for toUInt
    * true for toInt
    */
  val fnToInt = Module(new RecFNToIN(8, 24, 32))
  val convertRtz = uop(3,2) === 3.U
  fnToInt.io.in := recIn1
  fnToInt.io.roundingMode := Mux(convertRtz, "b001".U(3.W), request.roundingMode)
  fnToInt.io.signedOut := !(uop(3) && uop(0))

  val convertResult = Wire(UInt(32.W))
  val convertFlags  = Wire(UInt(5.W))
  convertResult := Mux(uop === "b1000".U, fNFromRecFN(8, 24, intToFn.io.out), fnToInt.io.out)
  convertFlags  := Mux(uop === "b1000".U, intToFn.io.exceptionFlags, fnToInt.io.intExceptionFlags)

  val sgnjresult = Wire(UInt(32.W))
  val sgnjSign = Mux(otherEn && uop === "b0001".U, request.src(0)(31),
    Mux(otherEn && uop === "b0010".U, !request.src(0)(31),
      Mux(otherEn && uop ==="b0011".U, request.src(0)(31) ^ request.src(1)(31), false.B)))
  sgnjresult := Cat(sgnjSign, request.src(1)(30,0))

  val in1classify = classifyRecFN(8, 24, recIn1)

  /** rec7 and rsqrt7 */
  val rec7En = (uop === "b0110".U) && otherEn
  val rsqrt7En = uop==="b0111".U && otherEn

  val rec7Module = Module(new Rec7Fn)
  rec7Module.in.data := request.src(1)
  rec7Module.in.classifyIn := in1classify
  rec7Module.in.roundingMode := request.roundingMode

  val rsqrt7Module = Module(new Rsqrt7Fn)
  rsqrt7Module.in.data := request.src(1)
  rsqrt7Module.in.classifyIn := in1classify

  val otherResult = Wire(UInt(32.W))
  val otherFlags = Wire(UInt(5.W))
  otherResult := Mux(uop(3), convertResult,
    Mux(uop(3,2) === "b00".U, sgnjresult,
      Mux(uop === "b0100".U, in1classify,
        Mux(uop==="b0110".U, rec7Module.out.data,
          Mux(uop==="b0111".U, rsqrt7Module.out.data, 0.U)))))

  otherFlags := Mux(rec7En, rec7Module.out.exceptionFlags,
    Mux(rsqrt7En,rsqrt7Module.out.exceptionFlags, convertFlags))

  /** collect results */
  result := Mux1H(Seq(
    unitSeleOH(0) -> fmaResult,
    unitSeleOH(2) -> compareResult,
    unitSeleOH(3) -> otherResult
  ))//todo: cannot select div output

  flags := Mux1H(Seq(
    unitSeleOH(0) -> mulAddRecFN.io.exceptionFlags,
    unitSeleOH(2) -> compareFlags,
    unitSeleOH(3) -> otherFlags
  ))

  response.adderMaskResp := result(0)
  response.data := result
  response.exceptionFlags := flags
  response.executeIndex := request.executeIndex
}

