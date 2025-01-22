package float

import chisel3._
import chisel3.util._
import division.srt.srt16._
import sqrt._

/** DIV
  * input
  * {{{
  * dividend = 0.1f  -> 1f +"00000" right extends to 32
  * divisor  = 0.1f  -> 1f +"00000" right extends to 32
  * }}}
  *
  * output = 0.01f or 0.1f, LSB 28bits effective
  * {{{
  * 0.01f: 28bits=01f f=sig=select(25,3)
  * 0.1f : 28bits=1f  f=sig=select(26,4)
  * }}}
  *
  * SQRT
  * {{{
  * expLSB   rawExpLSB    Sig             SigIn     expOut
  *      0           1    1.xxxx>>2<<1    1xxxx0    rawExp/2 +1 + bias
  *      1           0    1.xxxx>>2       01xxxx    rawExp/2 +1 + bias
  * }}}
  */
class DivSqrtMerge(expWidth: Int, sigWidth: Int) extends Module {
  val fpWidth = expWidth + sigWidth
  val calWidth = 28
  val input = IO(Flipped(DecoupledIO(new DivSqrtInput(expWidth, sigWidth))))
  val output = IO(ValidIO(new DivSqrtOutput(expWidth, sigWidth)))

  val iterWidth: Int = fpWidth + 6
  val sqrtIterWidth = sigWidth + 4
  val ohWidth = 5

  val opSqrtReg = RegEnable(input.bits.sqrt, false.B, input.fire)
  val roundingModeReg = RegEnable(input.bits.roundingMode, 0.U, input.fire)

  val rawA = rawFloatFromFN(expWidth, sigWidth, input.bits.dividend)
  val rawB = rawFloatFromFN(expWidth, sigWidth, input.bits.divisor)

  // Exceptions

  /** inf/inf and 0/0  => qNaN, set NV */
  val divInvalidCases = (rawA.isZero && rawB.isZero) || (rawA.isInf && rawB.isInf)
  /** normal/0  => inf, set DV */
  val divDivideZero = (!rawA.isNaN && !rawA.isInf && rawB.isZero)
  /** A = -Inf or -Normal => qNaN, set NV */
  val sqrtInvalidCases =
    !rawA.isNaN && !rawA.isZero && rawA.sign
  /** classified in flags
    *
    * contains all NV and DZ flags cases
    */
  val isNVorDZ =
    Mux(input.bits.sqrt,
      rawA.isSNaN || sqrtInvalidCases,
      rawA.isSNaN || rawB.isSNaN ||
        divInvalidCases ||
        divDivideZero
    )

  /** classified in output result
    *
    * qNaN output
    */
  val isNaN =
    Mux(input.bits.sqrt,
      rawA.isNaN || sqrtInvalidCases,
      rawA.isNaN || rawB.isNaN || divInvalidCases
    )
  val isInf = Mux(input.bits.sqrt, rawA.isInf, rawA.isInf || rawB.isZero)
  val isZero = Mux(input.bits.sqrt, rawA.isZero, rawA.isZero || rawB.isInf)

  val isNVorDZReg = RegEnable(isNVorDZ, false.B, input.fire)
  val isNaNReg = RegEnable(isNaN, false.B, input.fire)
  val isInfReg = RegEnable(isInf, false.B, input.fire)
  val isZeroReg = RegEnable(isZero, false.B, input.fire)

  /** invalid operation flag */
  val invalidExec = isNVorDZReg && isNaNReg

  /** DivideByZero flag */
  val infinitExec = isNVorDZReg && !isNaNReg

  val specialCaseA = rawA.isNaN || rawA.isInf || rawA.isZero
  val specialCaseB = rawB.isNaN || rawB.isInf || rawB.isZero
  val normalCaseDiv = !specialCaseA && !specialCaseB
  val normalCaseSqrt = !specialCaseA && !rawA.sign
  val normalCase = Mux(input.bits.sqrt, normalCaseSqrt, normalCaseDiv)
  val specialCase = !normalCase

  val fastValid = RegInit(false.B)
  fastValid := specialCase && input.fire

  // sign
  val signNext = Mux(input.bits.sqrt, rawA.isZero && rawA.sign, rawA.sign ^ rawB.sign)
  val signReg = RegEnable(signNext, input.fire)

  /** sqrt exp logic
    *
    * {{{
    * sExp first 2 bits
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

  // build SQRT input
  val expForSqrt = Cat(expstart, rawA.sExp(expWidth - 2, 0)) >> 1
  val sqrtFractIn = Mux(rawA.sExpIsEven, Cat(0.U(1.W), rawA.sig(sigWidth - 1, 0), 0.U(1.W)),
    Cat(rawA.sig(sigWidth - 1, 0), 0.U(2.W)))


  // build DIV Input
  val fractDividendIn = Wire(UInt((fpWidth).W))
  val fractDivisorIn = Wire(UInt((fpWidth).W))
  fractDividendIn := Cat(1.U(1.W), rawA.sig(sigWidth - 2, 0), 0.U(expWidth.W))
  fractDivisorIn := Cat(1.U(1.W), rawB.sig(sigWidth - 2, 0), 0.U(expWidth.W))


  val sqrtIter = Module(new SqrtIter(2, 2, sqrtIterWidth, sigWidth + 2))
  val divIter = Module(new SRT16Iter(fpWidth, fpWidth, fpWidth, 2, 2, 4, 4))

  val sqrtMuxIn     = Wire(new IterMuxIO(expWidth, sigWidth, fpWidth, ohWidth, iterWidth))
  val divMuxIn      = Wire(new IterMuxIO(expWidth, sigWidth, fpWidth, ohWidth, iterWidth))
  val divSqrtMuxOut = Wire(new IterMuxIO(expWidth, sigWidth, fpWidth, ohWidth, iterWidth))
  divSqrtMuxOut := Mux(opSqrtReg, sqrtMuxIn, divMuxIn)

  val divValid = input.valid && !input.bits.sqrt && normalCaseDiv
  val divReady = divIter.input.ready
  val sqrtValid = input.valid && input.bits.sqrt && normalCaseSqrt
  val sqrtReady = sqrtIter.input.ready

  val enable = divSqrtMuxOut.enable

  val partialCarryNext, partialSumNext = Wire(UInt(iterWidth.W))

  val partialCarry = RegEnable(partialCarryNext, 0.U(iterWidth.W), enable)
  val partialSum   = RegEnable(partialSumNext,   0.U(iterWidth.W), enable)

  partialSumNext := Mux(input.fire,
    divSqrtMuxOut.partialSumInit,
    divSqrtMuxOut.partialSumNext)
  partialCarryNext := Mux(input.fire,
    0.U,
    divSqrtMuxOut.partialCarryNext)

  val otf = OTF(2, fpWidth, ohWidth)(
    divSqrtMuxOut.quotient,
    divSqrtMuxOut.quotientMinusOne,
    divSqrtMuxOut.selectedQuotientOH)

  sqrtIter.input.valid := sqrtValid
  sqrtIter.input.bits.partialCarry := partialCarry
  sqrtIter.input.bits.partialSum := partialSum

  divIter.input.valid := divValid
  divIter.input.bits.partialSum := partialSum
  divIter.input.bits.partialCarry := partialCarry
  divIter.input.bits.divider := fractDivisorIn
  divIter.input.bits.counter := 8.U

  sqrtIter.respOTF.quotient := otf(0)
  sqrtIter.respOTF.quotientMinusOne := otf(1)
  divIter.respOTF.quotient := otf(0)
  divIter.respOTF.quotientMinusOne := otf(1)

  /** collect div result
    *
    * {{{
    * when B_sig > A_sig
    * divout = 0000,01xxx
    * exp need decrease by 1
    * }}}
    */
  val needRightShift = Wire(Bool())
  needRightShift := !divIter.resultOutput.bits.quotient(27)
  val sigPlusDiv = Wire(UInt((sigWidth+2).W))
  sigPlusDiv := Mux(needRightShift,
    divIter.resultOutput.bits.quotient(calWidth - 3, calWidth - sigWidth - 2) ## divIter.resultOutput.bits.reminder.orR,
    divIter.resultOutput.bits.quotient(calWidth - 2, calWidth - sigWidth - 1) ## divIter.resultOutput.bits.reminder.orR
  )

  // collect sqrt result
  val sigPlusSqrt = sqrtIter.resultOutput.bits.result(24, 1) ## (!sqrtIter.resultOutput.bits.zeroRemainder || sqrtIter.resultOutput.bits.result(0))

  // exp logic
  val expStoreNext, expToRound = Wire(UInt((expWidth + 2).W))

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
    * for div
    * rawA_S.sExp - rawB_S.sExp
    */
  expStoreNext := Mux(input.bits.sqrt,
    Cat(expForSqrt(7), expForSqrt(7), expForSqrt(7, 0)),
    (rawA.sExp - rawB.sExp).asUInt)
  val expStore = RegEnable(expStoreNext, 0.U((expWidth + 2).W), input.fire)
  expToRound := divSqrtMuxOut.expToRound

  val sigToRound = Wire(UInt((sigWidth+2).W))
  sigToRound := divSqrtMuxOut.sigToRound

  val roundresult = RoundingUnit(
    signReg,
    expToRound.asSInt,
    sigToRound,
    roundingModeReg,
    invalidExec,
    infinitExec,
    isNaNReg,
    isInfReg,
    isZeroReg)

  sqrtMuxIn.enable             := (sqrtValid && sqrtReady) || !sqrtIter.output.isLastCycle
  divMuxIn.enable              := (divValid && divReady) || !divIter.output.isLastCycle
  sqrtMuxIn.partialSumInit     := Cat("b11".U, sqrtFractIn)
  divMuxIn.partialSumInit      := fractDividendIn
  sqrtMuxIn.partialSumNext     := sqrtIter.output.partialSum
  sqrtMuxIn.partialCarryNext   := sqrtIter.output.partialCarry
  divMuxIn.partialSumNext      := divIter.output.partialSum
  divMuxIn.partialCarryNext    := divIter.output.partialCarry
  sqrtMuxIn.quotient           := sqrtIter.reqOTF.quotient
  sqrtMuxIn.quotientMinusOne   := sqrtIter.reqOTF.quotientMinusOne
  sqrtMuxIn.selectedQuotientOH := sqrtIter.reqOTF.selectedQuotientOH
  divMuxIn.quotient            := divIter.reqOTF.quotient
  divMuxIn.quotientMinusOne    := divIter.reqOTF.quotientMinusOne
  divMuxIn.selectedQuotientOH  := divIter.reqOTF.selectedQuotientOH
  sqrtMuxIn.sigToRound         := sigPlusSqrt
  sqrtMuxIn.expToRound         := expStore
  divMuxIn.sigToRound          := sigPlusDiv
  divMuxIn.expToRound          := expStore - needRightShift

  output.bits.result := roundresult(0)
  output.bits.exceptionFlags := roundresult(1)

  input.ready := divReady && sqrtReady
  output.valid := divIter.resultOutput.valid || sqrtIter.resultOutput.valid || fastValid
}

class IterMuxIO(expWidth: Int, sigWidth: Int, qWidth: Int, ohWidth: Int, iterWidth: Int)  extends Bundle{
  // enable signal
  val enable = Bool()

  // partialResult
  val partialSumInit = UInt(iterWidth.W)
  val partialSumNext = UInt(iterWidth.W)
  val partialCarryNext = UInt(iterWidth.W)

  // otf
  val quotient = UInt(qWidth.W)
  val quotientMinusOne = UInt(qWidth.W)
  val selectedQuotientOH = UInt(ohWidth.W)

  // collect output
  val expToRound = UInt((expWidth + 2).W)
  val sigToRound = UInt((sigWidth + 2).W)

}

