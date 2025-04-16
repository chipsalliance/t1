// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3.experimental.hierarchy.{instantiable, Instance, Instantiate}
import chisel3.{UInt, _}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
import chisel3.ltl._
import chisel3.ltl.Sequence._
import chisel3.properties.{Path, Property}
import hardfloat._
import org.chipsalliance.stdlib.GeneralOM
import org.chipsalliance.t1.rtl.decoder.{BoolField, Decoder}

object LaneFloatParam {
  implicit def rw: upickle.default.ReadWriter[LaneFloatParam] = upickle.default.macroRW
}

case class LaneFloatParam(eLen: Int, latency: Int, laneScale: Int)
    extends VFUParameter
    with SerializableModuleParameter {
  val datapathWidth: Int       = eLen * laneScale
  val decodeField:   BoolField = Decoder.float
  val inputBundle  = new LaneFloatRequest(datapathWidth)
  val outputBundle = new LaneFloatResponse(datapathWidth)
  override val NeedSplit: Boolean = false
}

/** UOP encoding
  *
  * for fmaEn up[3] = 1 for add/sub up[2] = 1 for reverse in rsub and reversed fmaEn up[1:0] for fmaEn sign, 0 for
  * positive, 1 for negative
  *
  * For compareModule 0001 EQ 0000 NQ 0010 LT 0011 LE 0100 GT 0101 GE
  *
  * 1000 min 1100 max
  *
  * none of these 3
  *
  * 0001 SGNJ 0010 SGNJN 0011 SGNJX 0100 classify 0101 merge 0110 sqrt7 0111 rec7
  *
  * 1000 to float 1010 to sint 1001 to uint 1110 to sint tr 1101 to uint tr
  */
class LaneFloatRequest(datapathWidth: Int) extends VFUPipeBundle {
  val sign         = Bool()
  val src          = Vec(3, UInt(datapathWidth.W))
  val opcode       = UInt(4.W)
  val unitSelet    = UInt(2.W)
  val floatMul     = Bool()
  val roundingMode = UInt(3.W)
  val executeIndex: UInt = UInt(2.W)
}

class LaneFloatResponse(datapathWidth: Int) extends VFUPipeBundle {
  val data           = UInt(datapathWidth.W)
  val adderMaskResp  = UInt((datapathWidth / 8).W)
  val exceptionFlags = UInt(5.W)
  val executeIndex: UInt = UInt(2.W)
}

class LaneFloatOM(parameter: LaneFloatParam) extends GeneralOM[LaneFloatParam, LaneFloat](parameter) {
  override def hasRetime: Boolean = true
}

@instantiable
class LaneFloat(val parameter: LaneFloatParam) extends VFUModule with SerializableModule[LaneFloatParam] {
  val omInstance: Instance[LaneFloatOM] = Instantiate(new LaneFloatOM(parameter))
  omInstance.retimeIn.foreach(_ := Property(Path(clock)))

  val response: LaneFloatResponse = Wire(new LaneFloatResponse(parameter.datapathWidth))
  val request:  LaneFloatRequest  = connectIO(response, true.B).asTypeOf(parameter.inputBundle)

  val responseVec: Seq[(UInt, UInt)] = Seq.tabulate(parameter.laneScale) { index =>
    val subRequest = Wire(new LaneFloatRequest(parameter.eLen))
    subRequest.elements.foreach { case (k, v) => v := request.elements(k) }
    subRequest.src.zip(request.src).foreach { case (sink, source) =>
      sink := cutUIntBySize(source, parameter.laneScale)(index)
    }

    val recIn0 = recFNFromFN(8, 24, subRequest.src(0))
    val recIn1 = recFNFromFN(8, 24, subRequest.src(1))
    val recIn2 = recFNFromFN(8, 24, subRequest.src(2))
    val raw0   = rawFloatFromRecFN(8, 24, recIn0)
    val raw1   = rawFloatFromRecFN(8, 24, recIn1)

    val uop = subRequest.opcode

    val unitSeleOH = UIntToOH(subRequest.unitSelet)
    val fmaEn      = unitSeleOH(0)
    val compareEn  = unitSeleOH(2)
    val otherEn    = unitSeleOH(3)

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
    val addsub = fmaEn && subRequest.opcode(3)
    val maf    = fmaEn && (uop(3, 2) === 0.U)
    val rmaf   = fmaEn && (uop(3, 2) === 1.U)

    val mulAddRecFN = Module(new MulAddRecFN(8, 24))
    val fmaIn0      = Mux(sub, recIn1, recIn0)
    val fmaIn1      = Mux(addsub, (BigInt(1) << (parameter.eLen - 1)).U, Mux(rmaf, recIn2, recIn1))
    val fmaIn2      = Mux(
      sub,
      recIn0,
      Mux(
        maf && !subRequest.floatMul,
        recIn2,
        Mux(
          maf && subRequest.floatMul,
          ((subRequest.src(0) ^ subRequest.src(1)) & (BigInt(1) << (parameter.eLen - 1)).U) << 1,
          recIn1
        )
      )
    )

    mulAddRecFN.io.op             := subRequest.opcode(1, 0)
    mulAddRecFN.io.a              := fmaIn0
    mulAddRecFN.io.b              := fmaIn1
    mulAddRecFN.io.c              := fmaIn2
    mulAddRecFN.io.roundingMode   := subRequest.roundingMode // todo decode it
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
      */
    val compareModule = Module(new CompareRecFN(8, 24))
    compareModule.io.a         := recIn1
    compareModule.io.b         := recIn0
    compareModule.io.signaling := uop(3, 1) === "b001".U || uop(3, 1) === "b010".U
    val compareResult  = Wire(UInt(32.W))
    val compareFlags   = Wire(UInt(5.W))
    val oneNaN         = raw0.isNaN ^ raw1.isNaN
    val compareNaN     = Mux(oneNaN, Mux(raw0.isNaN, subRequest.src(1), subRequest.src(0)), "x7fc00000".U)
    val hasNaN         = raw0.isNaN || raw1.isNaN
    val differentZeros = compareModule.io.eq && (subRequest.src(1)(31) ^ subRequest.src(0)(31))

    AssertProperty(
      BoolSequence(
        !unitSeleOH(
          2
        ) || (uop === "b0001".U || uop === "b0000".U || uop === "b0010".U || uop === "b0011".U || uop === "b0100".U || uop === "b0101".U || uop === "b1000".U || uop === "b1100".U)
      )
    )
    compareResult := Mux(
      uop === BitPat("b1?00") && hasNaN,
      compareNaN,
      Mux(
        uop === BitPat("b1?00"),
        Mux(
          (!uop(2) && compareModule.io.lt) || (uop(2) && compareModule.io.gt) || (differentZeros && (uop(2) ^ subRequest
            .src(1)(31))),
          subRequest.src(1),
          subRequest.src(0)
        ),
        Mux(
          uop === "b0011".U,
          compareModule.io.lt || compareModule.io.eq,
          Mux(
            uop === "b0101".U,
            compareModule.io.gt || compareModule.io.eq,
            Mux(
              uop === "b0010".U,
              compareModule.io.lt,
              Mux(
                uop === "b0100".U,
                compareModule.io.gt,
                Mux(uop === "b0000".U, !compareModule.io.eq, compareModule.io.eq)
              )
            )
          )
        )
      )
    )
    compareFlags  := compareModule.io.exceptionFlags

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
      */
    val intToFn = Module(new INToRecFN(32, 8, 24))
    intToFn.io.in             := subRequest.src(1)
    intToFn.io.signedIn       := subRequest.sign
    intToFn.io.roundingMode   := subRequest.roundingMode
    intToFn.io.detectTininess := false.B

    /** io.signedOut define output sign
      *
      * false for toUInt true for toInt
      */
    val fnToInt    = Module(new RecFNToIN(8, 24, 32))
    val convertRtz = uop(3, 2) === 3.U
    fnToInt.io.in           := recIn1
    fnToInt.io.roundingMode := Mux(convertRtz, "b001".U(3.W), subRequest.roundingMode)
    fnToInt.io.signedOut    := !(uop(3) && uop(0))

    val convertResult = Wire(UInt(32.W))
    val convertFlags  = Wire(UInt(5.W))
    convertResult := Mux(uop === "b1000".U, fNFromRecFN(8, 24, intToFn.io.out), fnToInt.io.out)
    convertFlags  := Mux(uop === "b1000".U, intToFn.io.exceptionFlags, fnToInt.io.intExceptionFlags)

    val sgnjresult = Wire(UInt(32.W))
    val sgnjSign   = Mux(
      otherEn && uop === "b0001".U,
      subRequest.src(0)(31),
      Mux(
        otherEn && uop === "b0010".U,
        !subRequest.src(0)(31),
        Mux(otherEn && uop === "b0011".U, subRequest.src(0)(31) ^ subRequest.src(1)(31), false.B)
      )
    )
    sgnjresult := Cat(sgnjSign, subRequest.src(1)(30, 0))

    val in1classify = classifyRecFN(8, 24, recIn1)

    /** rec7 and rsqrt7 */
    val rec7En   = (uop === "b0110".U) && otherEn
    val rsqrt7En = uop === "b0111".U && otherEn

    val rec7Module: Instance[Rec7Fn] = Instantiate(new Rec7Fn(Rec7FnParameter()))
    rec7Module.io.in.data         := subRequest.src(1)
    rec7Module.io.in.classifyIn   := in1classify
    rec7Module.io.in.roundingMode := subRequest.roundingMode

    val rsqrt7Module: Instance[Rsqrt7Fn] = Instantiate(new Rsqrt7Fn(Rsqrt7FnParameter()))
    rsqrt7Module.io.in.data       := subRequest.src(1)
    rsqrt7Module.io.in.classifyIn := in1classify

    val otherResult = Wire(UInt(32.W))
    val otherFlags  = Wire(UInt(5.W))
    otherResult := Mux(
      uop(3),
      convertResult,
      Mux(
        uop(3, 2) === "b00".U,
        sgnjresult,
        Mux(
          uop === "b0100".U,
          in1classify,
          Mux(uop === "b0110".U, rec7Module.io.out.data, Mux(uop === "b0111".U, rsqrt7Module.io.out.data, 0.U))
        )
      )
    )

    otherFlags := Mux(
      rec7En,
      rec7Module.io.out.exceptionFlags,
      Mux(rsqrt7En, rsqrt7Module.io.out.exceptionFlags, convertFlags)
    )

    /** collect results */
    result := Mux1H(
      Seq(
        unitSeleOH(0) -> fmaResult,
        unitSeleOH(2) -> compareResult,
        unitSeleOH(3) -> otherResult
      )
    ) // todo: cannot select div output

    flags := Mux1H(
      Seq(
        unitSeleOH(0) -> mulAddRecFN.io.exceptionFlags,
        unitSeleOH(2) -> compareFlags,
        unitSeleOH(3) -> otherFlags
      )
    )
    (result, flags)
  }

  response.adderMaskResp  := VecInit(responseVec.map(_._1(parameter.eLen / 8 - 1, 0))).asUInt
  response.data           := VecInit(responseVec.map(_._1)).asUInt
  response.exceptionFlags := VecInit(responseVec.map(_._2)).reduce(_ | _)
  response.executeIndex   := request.executeIndex
}
