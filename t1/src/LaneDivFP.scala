// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.hierarchy.{public, Instance, instantiable, Instantiate}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
import float._
import sqrt._
import division.srt.srt16._
import org.chipsalliance.stdlib.GeneralOM
import org.chipsalliance.t1.rtl.decoder.{BoolField, Decoder}

object LaneDivFPParam {
  implicit def rw: upickle.default.ReadWriter[LaneDivFPParam] = upickle.default.macroRW
}
case class LaneDivFPParam(datapathWidth: Int, latency: Int) extends VFUParameter with SerializableModuleParameter {
  val decodeField: BoolField = Decoder.divider
  val inputBundle  = new LaneDivFPRequest(datapathWidth)
  val outputBundle = new LaneDivFPResponse(datapathWidth)
  override val NeedSplit:   Boolean = true
  override val singleCycle: Boolean = false
}

class LaneDivFPRequest(datapathWidth: Int) extends VFUPipeBundle {
  val src: Vec[UInt] = Vec(2, UInt(datapathWidth.W))
  val opcode = UInt(4.W)
  val sign:         Bool = Bool()
  // execute index in group
  val executeIndex: UInt = UInt(2.W)
  // csr
  // val vSew: UInt = UInt(2.W)
  val roundingMode = UInt(3.W)
}

class LaneDivFPResponse(datapathWidth: Int) extends VFUPipeBundle {
  val data: UInt = UInt(datapathWidth.W)
  val exceptionFlags = UInt(5.W)
  val executeIndex: UInt = UInt(2.W)
  val busy:         Bool = Bool()
}

class LaneDivFPOM(parameter: LaneDivFPParam) extends GeneralOM[LaneDivFPParam, LaneDivFP](parameter)

@instantiable
class LaneDivFP(val parameter: LaneDivFPParam) extends VFUModule with SerializableModule[LaneDivFPParam] {
  val omInstance: Instance[LaneDivFPOM] = Instantiate(new LaneDivFPOM(parameter))
  val response:      LaneDivFPResponse = Wire(new LaneDivFPResponse(parameter.datapathWidth))
  val responseValid: Bool              = Wire(Bool())
  val request:       LaneDivFPRequest  = connectIO(response, responseValid).asTypeOf(parameter.inputBundle)

  val uop = request.opcode

  val integerEn = uop(3) === 0.U
  val isRem     = uop === "b0001".U
  val fractEn   = !integerEn
  val rdiv      = (uop === "b1010".U)
  val sqrt      = (uop === "b1001".U)

  val divIn0 = Mux(rdiv, request.src(0), request.src(1))
  val divIn1 = Mux(rdiv, request.src(1), request.src(0))

  val wrapper: Instance[SRTFPWrapper] = Instantiate(new SRTFPWrapper(8, 24))
  wrapper.input.bits.a            := Mux(fractEn, divIn0.asSInt, request.src(1).asSInt)
  wrapper.input.bits.b            := Mux(fractEn, divIn1.asSInt, request.src(0).asSInt)
  wrapper.input.bits.signIn       := request.sign
  wrapper.input.bits.opFloat      := fractEn
  wrapper.input.bits.opSqrt       := sqrt
  wrapper.input.bits.opRem        := isRem
  wrapper.input.valid             := requestRegValid
  wrapper.input.bits.roundingMode := request.roundingMode

  val requestFire: Bool = vfuRequestFire
  val indexReg:    UInt = RegEnable(request.executeIndex, 0.U, requestFire)
  response.busy := RegEnable(requestFire, false.B, requestFire ^ responseIO.valid)

  vfuRequestReady.foreach(_ := wrapper.input.ready)
  response.executeIndex   := indexReg
  responseValid           := wrapper.output.valid
  response.data           := wrapper.output.bits.result
  response.exceptionFlags := wrapper.output.bits.exceptionFlags
}

/** 32-bits Divider-Sqrt for integer/float division and square-root
  *
  * DIV/FDIV component
  *
  * FDIV input
  * {{{
  * dividend = 0.1f  -> 1f +"00000" right extends to 32
  * divisor  = 0.1f  -> 1f +"00000" right extends to 32
  * }}}
  *
  * FDIV output = 0.01f or 0.1f, LSB 28bits effective
  * {{{
  * 0.01f: 28bits=01f f=sig=select(25,3)
  * 0.1f : 28bits=1f  f=sig=select(26,4)
  * }}}
  *
  * SQRT component
  * {{{
  * expLSB   rawExpLSB    Sig             SigIn     expOut
  *      0           1    1.xxxx>>2<<1    1xxxx0    rawExp/2 +1 + bias
  *      1           0    1.xxxx>>2       01xxxx    rawExp/2 +1 + bias
  * }}}
  */
@instantiable
class SRTFPWrapper(expWidth: Int, sigWidth: Int) extends Module {
  class SRTIn  extends Bundle {
    val a            = SInt(32.W)
    val b            = SInt(32.W)
    val signIn       = Bool()
    val opFloat      = Bool()
    val opSqrt       = Bool()
    val opRem        = Bool()
    val roundingMode = UInt(3.W)
  }
  class SRTOut extends Bundle {
    val reminder       = UInt(32.W)
    val quotient       = UInt(32.W)
    val result         = UInt(32.W)
    val exceptionFlags = UInt(5.W)
  }
  @public
  val input = IO(Flipped(DecoupledIO(new SRTIn)))
  @public
  val output = IO(ValidIO(new SRTOut))

  val fpWidth                = expWidth + sigWidth
  val floatDivCalculateWidth = 28
  val iterWidth: Int = fpWidth + 6
  val sqrtIterWidth = sigWidth + 4
  val ohWidth       = 5

  val opFloat         = input.bits.opFloat
  val opSqrt          = input.bits.opSqrt
  val opFloatReg      = RegEnable(opFloat, false.B, input.fire)
  val opSqrtReg       = RegEnable(input.bits.opSqrt, false.B, input.fire)
  val opRemReg        = RegEnable(input.bits.opRem, false.B, input.fire)
  val roundingModeReg = RegEnable(input.bits.roundingMode, 0.U(5.W), input.fire)

  val rawA = rawFloatFromFN(expWidth, sigWidth, input.bits.a)
  val rawB = rawFloatFromFN(expWidth, sigWidth, input.bits.b)

  /** inf/inf and 0/0  => qNaN, set NV */
  val divInvalidCases = (rawA.isZero && rawB.isZero) || (rawA.isInf && rawB.isInf)

  /** normal/0  => inf, set DV */
  val divDivideZero = (!rawA.isNaN && !rawA.isInf && rawB.isZero)

  /** A = -Inf or -Normal => qNaN, set NV */
  val sqrtInvalidCases =
    !rawA.isNaN && !rawA.isZero && rawA.sign

  /** all NV and DZ flags cases */
  val isNVorDZ =
    Mux(
      input.bits.opSqrt,
      rawA.isSNaN || sqrtInvalidCases,
      rawA.isSNaN || rawB.isSNaN ||
        divInvalidCases ||
        divDivideZero
    )

  /** all cases resulting in qNaN */
  val isNaN  =
    Mux(input.bits.opSqrt, rawA.isNaN || sqrtInvalidCases, rawA.isNaN || rawB.isNaN || divInvalidCases)
  val isInf  = Mux(input.bits.opSqrt, rawA.isInf, rawA.isInf || rawB.isZero)
  val isZero = Mux(input.bits.opSqrt, rawA.isZero, rawA.isZero || rawB.isInf)

  val isNVorDZReg = RegEnable(isNVorDZ, false.B, input.fire)
  val isNaNReg    = RegEnable(isNaN, false.B, input.fire)
  val isInfReg    = RegEnable(isInf, false.B, input.fire)
  val isZeroReg   = RegEnable(isZero, false.B, input.fire)

  /** invalid operation flag */
  val invalidExec = isNVorDZReg && isNaNReg

  /** DivideByZero flag */
  val infinitExec = isNVorDZReg && !isNaNReg

  val specialCaseA   = rawA.isNaN || rawA.isInf || rawA.isZero
  val specialCaseB   = rawB.isNaN || rawB.isInf || rawB.isZero
  val normalCaseDiv  = !specialCaseA && !specialCaseB
  val normalCaseSqrt = !specialCaseA && !rawA.sign
  val normalCase     = Mux(input.bits.opSqrt, normalCaseSqrt, normalCaseDiv)
  val specialCase    = !normalCase

  val bypassFloat       = specialCase && opFloat
  val floatSpecialValid = RegInit(false.B)
  floatSpecialValid := bypassFloat && input.fire

  val signNext = Mux(input.bits.opSqrt, rawA.isZero && rawA.sign, rawA.sign ^ rawB.sign)
  val signReg  = RegEnable(signNext, false.B, input.fire)

  /** sqrt logic
    *
    * {{{
    * rawA_S.sExp first 2 bits
    * 00 -> 10 (subnormal)
    * 01 -> 11 (true exp negative)
    * 10 -> 00 (true exp positive)
    * }}}
    */
  val expfirst2 = UIntToOH(rawA.sExp(expWidth, expWidth - 1))

  /** expfirst2(3) never happens */
  val expstart = Mux1H(
    Seq(
      expfirst2(0) -> "b10".U,
      expfirst2(1) -> "b11".U,
      expfirst2(2) -> "b00".U
    )
  )

  val expForSqrt   = Cat(expstart, rawA.sExp(expWidth - 2, 0)) >> 1
  val sqrtExpIsOdd = !rawA.sExp(0)
  val sqrtFractIn  =
    Mux(sqrtExpIsOdd, Cat("b0".U(1.W), rawA.sig(sigWidth - 1, 0), 0.U(1.W)), Cat(rawA.sig(sigWidth - 1, 0), 0.U(2.W)))

  val sqrtIter = Module(new SqrtIter(2, 2, sqrtIterWidth, sigWidth + 2))

  // build FDIV input
  val fractDividendIn = Wire(UInt((fpWidth).W))
  val fractDivisorIn  = Wire(UInt((fpWidth).W))
  fractDividendIn := Cat(1.U(1.W), rawA.sig(sigWidth - 2, 0), 0.U(expWidth.W))
  fractDivisorIn  := Cat(1.U(1.W), rawB.sig(sigWidth - 2, 0), 0.U(expWidth.W))

//-----------------------Integer----------------------

  val divIter = Module(new SRT16Iter(fpWidth, fpWidth, fpWidth, 2, 2, 4, 4))

  val abs = Instantiate(new Abs(32))
  abs.io.aIn    := input.bits.a
  abs.io.bIn    := input.bits.b
  abs.io.signIn := input.bits.signIn
  val negative = abs.io.aSign ^ abs.io.bSign

  /** divided by zero detection */
  val divideZero = (input.bits.b === 0.S)

  /** bigger divisor detection */
  val dividend      = Wire(UInt(33.W))
  val divisor       = Wire(UInt(33.W))
  val gap           = Wire(UInt(34.W))
  val biggerdivisor = Wire(Bool())
  dividend      := abs.io.aOut
  divisor       := abs.io.bOut
  gap           := divisor +& (-dividend)
  biggerdivisor := gap(33) && !(gap(32, 0).orR === false.B)

  // when dividezero or biggerdivisor, bypass SRT
  val bypassInteger = (divideZero || biggerdivisor) && input.fire && !opFloat

  /** SRT16 initial process logic containing a leading zero counter */
  // extend one bit for calculation
  val zeroHeadDividend = Wire(UInt(6.W))
  val zeroHeadDivisor  = Wire(UInt(6.W))
  zeroHeadDividend := float.countLeadingZeros(abs.io.aOut)
  zeroHeadDivisor  := float.countLeadingZeros(abs.io.bOut)
  // sub = zeroHeadDivider - zeroHeadDividend
  val sub = Wire(UInt(6.W))
  sub := (-zeroHeadDividend) +& zeroHeadDivisor
  // needComputerWidth: Int = zeroHeadDivider - zeroHeadDividend + 2
  val needComputerWidth = Wire(UInt(7.W))
  needComputerWidth := sub +& 2.U
  // guardWidth = 4 - needComputerWidth % 4(except needComputerWidth mod4 = 0 => 0 )
  val guardSele  = UIntToOH(needComputerWidth(1, 0))
  val guardWidth = Mux1H(
    Seq(
      guardSele(0) -> 0.U(2.W),
      guardSele(1) -> 3.U(2.W),
      guardSele(2) -> 2.U(2.W),
      guardSele(3) -> 1.U(2.W)
    )
  )
  // counter: Int = (needComputerWidth + guardWidth) / radixLog2
  val counter    = ((needComputerWidth +& guardWidth) >> 2).asUInt

  val leftShiftWidthDividend = Wire(UInt(6.W))
  val leftShiftWidthDivisor  = Wire(UInt(6.W))
  leftShiftWidthDividend := zeroHeadDividend +& -Cat(0.U(4.W), guardWidth) + 3.U
  leftShiftWidthDivisor  := zeroHeadDivisor(4, 0)

  // control signals used in SRT post-process
  val negativeSRT        = RegEnable(negative, false.B, divIter.input.fire)
  val zeroHeadDivisorSRT = RegEnable(zeroHeadDivisor, 0.U(6.W), divIter.input.fire)
  val dividendSignSRT    = RegEnable(abs.io.aSign, false.B, divIter.input.fire)

  // keep for one cycle
  val divideZeroReg    = RegEnable(divideZero, false.B, input.fire)
  val biggerdivisorReg = RegEnable(biggerdivisor, false.B, input.fire)
  val bypassIntegerReg = RegNext(bypassInteger, false.B)
  val dividendInputReg = RegEnable(input.bits.a.asUInt, 0.U(32.W), input.fire)

  val divDividend = Wire(UInt((fpWidth + 3).W))
  val divDivisor  = Wire(UInt(fpWidth.W))
  divDividend := Mux(opFloat || (input.fire && opFloat), fractDividendIn, abs.io.aOut << leftShiftWidthDividend)
  divDivisor  := Mux(opFloat || (input.fire && opFloat), fractDivisorIn, abs.io.bOut << leftShiftWidthDivisor)

  // Float iteration Mux for FDIV and SQRT
  val sqrtMuxIn     = Wire(new IterMuxIO(expWidth, sigWidth, fpWidth, ohWidth, iterWidth))
  val divMuxIn      = Wire(new IterMuxIO(expWidth, sigWidth, fpWidth, ohWidth, iterWidth))
  val divSqrtMuxOut = Wire(new IterMuxIO(expWidth, sigWidth, fpWidth, ohWidth, iterWidth))
  divSqrtMuxOut := Mux(opSqrtReg || (input.bits.opSqrt && input.fire), sqrtMuxIn, divMuxIn)

  val divValid  = input.valid && !bypassInteger && !bypassFloat && !opSqrt
  val sqrtValid = input.valid && input.bits.opSqrt && normalCaseSqrt
  val divReady  = divIter.input.ready
  val sqrtReady = sqrtIter.input.ready

  val enable = divSqrtMuxOut.enable

  val partialCarryNext, partialSumNext = Wire(UInt(iterWidth.W))

  val partialCarry = RegEnable(partialCarryNext, 0.U(iterWidth.W), enable)
  val partialSum   = RegEnable(partialSumNext, 0.U(iterWidth.W), enable)

  partialSumNext   := Mux(input.fire, divSqrtMuxOut.partialSumInit, divSqrtMuxOut.partialSumNext)
  partialCarryNext := Mux(input.fire, 0.U, divSqrtMuxOut.partialCarryNext)

  val otf =
    OTF(2, fpWidth, ohWidth)(divSqrtMuxOut.quotient, divSqrtMuxOut.quotientMinusOne, divSqrtMuxOut.selectedQuotientOH)

  sqrtIter.input.valid             := sqrtValid
  sqrtIter.input.bits.partialCarry := partialCarry
  sqrtIter.input.bits.partialSum   := partialSum

  divIter.input.valid             := divValid
  divIter.input.bits.partialSum   := partialSum
  divIter.input.bits.partialCarry := partialCarry
  divIter.input.bits.divider      := divDivisor
  divIter.input.bits.counter      := Mux(opFloatReg || (opFloat && input.fire), 8.U, counter)

  sqrtIter.respOTF.quotient         := otf(0)
  sqrtIter.respOTF.quotientMinusOne := otf(1)
  divIter.respOTF.quotient          := otf(0)
  divIter.respOTF.quotientMinusOne  := otf(1)

  // calculate quotient and remainder in ABS
  val quotientAbs  = Wire(UInt(32.W))
  val remainderAbs = Wire(UInt(32.W))
  quotientAbs  := divIter.resultOutput.bits.quotient
  remainderAbs := divIter.resultOutput.bits.reminder >> zeroHeadDivisorSRT(4, 0)

  val intQuotient  = Wire(UInt(32.W))
  val intRemainder = Wire(UInt(32.W))
  val intResult    = Wire(UInt(32.W))

  /** divInteger result collect
    *
    * when divisor equals to zero, the quotient has all bits set and the remainder equals the dividend
    */
  intQuotient  := Mux(
    divideZeroReg,
    "hffffffff".U(32.W),
    Mux(
      biggerdivisorReg,
      0.U,
      Mux(negativeSRT, -quotientAbs, quotientAbs)
    )
  )
  intRemainder := Mux(
    divideZeroReg || biggerdivisorReg,
    dividendInputReg,
    Mux(dividendSignSRT, -remainderAbs, remainderAbs)
  )

  intResult := Mux(opRemReg, intRemainder, intQuotient)

  // -------------------- FP result collect -----------------------------------------
  /** collect div result
    *
    * {{{
    * when B_sig > A_sig
    * divout = 0000,01xxx
    * exp need decrease by 1
    * }}}
    */
  val needRightShift = !divIter.resultOutput.bits.quotient(27)

  // collect sig results for sqrt and float
  val sigPlusSqrt = sqrtIter.resultOutput.bits
    .result(24, 1) ## (!sqrtIter.resultOutput.bits.zeroRemainder || sqrtIter.resultOutput.bits.result(0))
  val sigPlusDiv  = Wire(UInt((sigWidth + 2).W))
  sigPlusDiv := Mux(
    needRightShift,
    divIter.resultOutput.bits.quotient(
      floatDivCalculateWidth - 3,
      floatDivCalculateWidth - sigWidth - 2
    ) ## divIter.resultOutput.bits.reminder.orR,
    divIter.resultOutput.bits.quotient(
      floatDivCalculateWidth - 2,
      floatDivCalculateWidth - sigWidth - 1
    ) ## divIter.resultOutput.bits.reminder.orR
  )

  val expSelected, expToRound = Wire(UInt((expWidth + 2).W))

  /** expStore is 10bits SInt
    *
    * for sqrt
    * {{{
    * expForSqrt(7,0) effective is 8bits, MSB is sign
    * extends 2 sign bit in MSB
    * expStoreNext = 10bits
    * input =   axxxxxxx
    * out   = aaaxxxxxxx
    * }}}
    *
    * for div rawA_S.sExp - rawB_S.sExp
    */
  expSelected := Mux(
    input.bits.opSqrt,
    Cat(expForSqrt(7), expForSqrt(7), expForSqrt(7, 0)),
    (rawA.sExp - rawB.sExp).asUInt
  )
  val expSelectedReg = RegEnable(expSelected, 0.U((expWidth + 2).W), input.fire)

  /** add this mechanism to rounding unit? */
  expToRound := divSqrtMuxOut.expToRound

  val sigToRound = Wire(UInt((sigWidth + 2).W))
  sigToRound := divSqrtMuxOut.sigToRound

  sqrtMuxIn.enable             := (sqrtValid && sqrtReady) || !sqrtIter.output.isLastCycle
  sqrtMuxIn.partialSumInit     := Cat("b11".U, sqrtFractIn)
  sqrtMuxIn.partialSumNext     := sqrtIter.output.partialSum
  sqrtMuxIn.partialCarryNext   := sqrtIter.output.partialCarry
  sqrtMuxIn.quotient           := sqrtIter.reqOTF.quotient
  sqrtMuxIn.quotientMinusOne   := sqrtIter.reqOTF.quotientMinusOne
  sqrtMuxIn.selectedQuotientOH := sqrtIter.reqOTF.selectedQuotientOH
  sqrtMuxIn.sigToRound         := sigPlusSqrt
  sqrtMuxIn.expToRound         := expSelectedReg

  divMuxIn.enable             := (divValid && divReady) || !divIter.output.isLastCycle
  divMuxIn.partialSumInit     := divDividend
  divMuxIn.partialSumNext     := divIter.output.partialSum
  divMuxIn.partialCarryNext   := divIter.output.partialCarry
  divMuxIn.quotient           := divIter.reqOTF.quotient
  divMuxIn.quotientMinusOne   := divIter.reqOTF.quotientMinusOne
  divMuxIn.selectedQuotientOH := divIter.reqOTF.selectedQuotientOH
  divMuxIn.sigToRound         := sigPlusDiv
  divMuxIn.expToRound         := expSelectedReg - needRightShift

  val roundResult = RoundingUnit(
    signReg,
    expToRound.asSInt,
    sigToRound,
    roundingModeReg,
    invalidExec,
    infinitExec,
    isNaNReg,
    isInfReg,
    isZeroReg
  )

  input.ready  := divReady && sqrtReady
  output.valid := divIter.resultOutput.valid | bypassIntegerReg | sqrtIter.resultOutput.valid | floatSpecialValid

  output.bits.quotient       := intQuotient
  output.bits.reminder       := intRemainder
  output.bits.result         := Mux(opFloatReg, roundResult(0), intResult)
  output.bits.exceptionFlags := roundResult(1)
}
