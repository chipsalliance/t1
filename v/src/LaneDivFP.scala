package v

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
import division.srt.SRT
import v.{BoolField, Decoder, VFUModule, VFUParameter}
import float._
import sqrt.SquareRoot

object LaneDivFPParam {
  implicit def rw: upickle.default.ReadWriter[LaneDivFPParam] = upickle.default.macroRW
}
case class LaneDivFPParam(datapathWidth: Int) extends VFUParameter with SerializableModuleParameter {
  val decodeField: BoolField = Decoder.divider
  val inputBundle  = new LaneDivFPRequest(datapathWidth)
  val outputBundle = new LaneDivFPResponse(datapathWidth)
}

class LaneDivFPRequest(datapathWidth: Int) extends Bundle {
  val src:  Vec[UInt] = Vec(2, UInt(datapathWidth.W))
  val opcode = UInt(4.W)
  val sign: Bool = Bool()
  // execute index in group
  val executeIndex: UInt = UInt(2.W)
  // csr
  // val vSew: UInt = UInt(2.W)
  val roundingMode = UInt(3.W)
}

class LaneDivFPResponse(datapathWidth: Int) extends Bundle {
  val data: UInt = UInt(datapathWidth.W)
  val exceptionFlags = UInt(5.W)
  val executeIndex: UInt = UInt(2.W)
  val busy: Bool = Bool()
}

class LaneDivFP(val parameter: LaneDivFPParam) extends VFUModule(parameter) with SerializableModule[LaneDivFPParam] {
  val response: LaneDivFPResponse = Wire(new LaneDivFPResponse(parameter.datapathWidth))
  val request: LaneDivFPRequest = connectIO(response).asTypeOf(parameter.inputBundle)

  val uop = request.opcode

  val integerEn = uop(3)===0.U
  val isRem = uop==="b0001".U
  val fractEn = !integerEn
  val rdiv = (uop === "b1010".U)
  val sqrt = (uop === "b1001".U)

  val divIn0 = Mux(rdiv, request.src(0), request.src(1))
  val divIn1 = Mux(rdiv, request.src(1), request.src(0))

  val wrapper = Module(new SRTFPWrapper(8,24))
  wrapper.input.bits.a       := Mux(fractEn, divIn0.asSInt, request.src(1).asSInt)
  wrapper.input.bits.b       := Mux(fractEn, divIn1.asSInt, request.src(0).asSInt)
  wrapper.input.bits.signIn  := request.sign
  wrapper.input.bits.fractEn := fractEn
  wrapper.input.bits.sqrt    := sqrt
  wrapper.input.bits.rem     := isRem
  wrapper.input.valid        := requestIO.valid
  wrapper.input.bits.roundingMode := request.roundingMode

  val requestFire: Bool = requestIO.fire
  val indexReg: UInt = RegEnable(request.executeIndex, 0.U, requestFire)
  response.busy := RegEnable(requestFire, false.B, requestFire ^ responseIO.valid)

  response.executeIndex   := indexReg
  requestIO.ready         := wrapper.input.ready
  responseIO.valid        := wrapper.output.valid
  response.data           := wrapper.output.bits.result
  response.exceptionFlags := wrapper.output.bits.exceptionFlags
}

/** 32-bits Divider for signed and unsigned division based on SRT16 with CSA
  *
  * Input:
  * dividend and divisor
  * signIn: true for signed input
  *
  * Component:
  * {{{
  * divided by zero detection
  * bigger divisor detection
  * SRT16 initial process logic containing a leading zero counter
  * SRT16 recurrence module imported from dependencies/arithmetic
  * SRT16 post-process logic
  * }}}
  */
class SRTFPWrapper(expWidth: Int, sigWidth: Int) extends Module {
  class SRTIn extends Bundle {
    val a = SInt(32.W)
    val b = SInt(32.W)
    val signIn = Bool()
    val fractEn = Bool()
    val sqrt = Bool()
    val rem = Bool()
    val roundingMode = UInt(3.W)
  }
  class SRTOut extends Bundle {
    val reminder = UInt(32.W)
    val quotient = UInt(32.W)
    val result = UInt(32.W)
    val exceptionFlags = UInt(5.W)
  }
  val input = IO(Flipped(DecoupledIO(new SRTIn)))
  val output = IO(ValidIO(new SRTOut))

  val fpWidth = expWidth + sigWidth
  val calWidth = 28

  val fractEn = input.bits.fractEn
  val sqrtEn  = input.bits.sqrt
  val fractEnReg = RegEnable(fractEn, false.B, input.fire)
  val opSqrtReg  = RegEnable(input.bits.sqrt, false.B, input.fire)
  val remReg     = RegEnable(input.bits.rem,  false.B, input.fire)
  val rmReg      = RegEnable(input.bits.roundingMode, 0.U(5.W), input.fire)

  val rawA = rawFloatFromFN(expWidth, sigWidth, input.bits.a)
  val rawB = rawFloatFromFN(expWidth, sigWidth, input.bits.b)

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
  val isNaNReg    = RegEnable(isNaN, false.B, input.fire)
  val isInfReg    = RegEnable(isInf, false.B, input.fire)
  val isZeroReg   = RegEnable(isZero, false.B, input.fire)

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
  fastValid := specialCase && input.fire && fractEn



  // sign
  val signNext = Mux(input.bits.sqrt, rawA.isZero && rawA.sign, rawA.sign ^ rawB.sign)
  val signReg = RegEnable(signNext, false.B, input.fire)

  /** sqrt logic
    *
    * {{{
    * rawA_S.sExp first 2 bits
    * 00 -> 10 (subnormal)
    * 01 -> 11 (true exp negative)
    * 10 -> 00 (true exp positive)
    * }}}
    *
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

  val expForSqrt = Cat(expstart, rawA.sExp(expWidth - 2, 0)) >> 1
  val sqrtExpIsOdd = !rawA.sExp(0)
  val sqrtFractIn = Mux(sqrtExpIsOdd, Cat("b0".U(1.W), rawA.sig(sigWidth - 1, 0), 0.U(1.W)),
    Cat(rawA.sig(sigWidth - 1, 0), 0.U(2.W)))

  val SqrtModule = Module(new SquareRoot(2, 2, sigWidth + 2, sigWidth + 2))
  SqrtModule.input.bits.operand := sqrtFractIn
  SqrtModule.input.valid := input.valid && input.bits.sqrt && normalCaseSqrt


  // div FP input
  val fractDividendIn = Wire(UInt((fpWidth).W))
  val fractDivisorIn  = Wire(UInt((fpWidth).W))
  fractDividendIn := Cat(1.U(1.W), rawA.sig(sigWidth - 2, 0), 0.U(expWidth.W))
  fractDivisorIn  := Cat(1.U(1.W), rawB.sig(sigWidth - 2, 0), 0.U(expWidth.W))


//-----------------------Integer----------------------
  val abs = Module(new Abs(32))
  abs.io.aIn := input.bits.a
  abs.io.bIn := input.bits.b
  abs.io.signIn := input.bits.signIn
  val negative = abs.io.aSign ^ abs.io.bSign

  val divModule: SRT = Module(new SRT(32, 32, 32, radixLog2 = 4))

  /** divided by zero detection */
  val divideZero = (input.bits.b === 0.S)

  /** bigger divisor detection */
  val dividend = Wire(UInt(33.W))
  val divisor  = Wire(UInt(33.W))
  val gap      = Wire(UInt(34.W))
  val biggerdivisor = Wire(Bool())
  dividend := abs.io.aOut
  divisor  := abs.io.bOut
  gap := divisor +& (-dividend)
  biggerdivisor := gap(33) && !(gap(32, 0).orR === false.B)

  // bypass
  val bypassSRT = (divideZero || biggerdivisor) && input.fire && !fractEn

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
  val guardSele = UIntToOH(needComputerWidth(1,0))
  val guardWidth = Mux1H(Seq(
    guardSele(0) -> 0.U(2.W),
    guardSele(1) -> 3.U(2.W),
    guardSele(2) -> 2.U(2.W),
    guardSele(3) -> 1.U(2.W),
  ))
  // counter: Int = (needComputerWidth + guardWidth) / radixLog2
  val counter = ((needComputerWidth +& guardWidth) >> 2).asUInt

  val leftShiftWidthDividend = Wire(UInt(6.W))
  val leftShiftWidthDivisor  = Wire(UInt(6.W))
  leftShiftWidthDividend := zeroHeadDividend +& -Cat(0.U(4.W), guardWidth) + 3.U
  leftShiftWidthDivisor := zeroHeadDivisor(4, 0)

  // control signals used in SRT post-process
  val negativeSRT        = RegEnable(negative,        false.B,  divModule.input.fire)
  val zeroHeadDivisorSRT = RegEnable(zeroHeadDivisor, 0.U(6.W), divModule.input.fire)
  val dividendSignSRT    = RegEnable(abs.io.aSign,    false.B,  divModule.input.fire)

  // keep for one cycle
  val divideZeroReg    = RegEnable(divideZero, false.B, input.fire)
  val biggerdivisorReg = RegEnable(biggerdivisor, false.B, input.fire)
  val bypassSRTReg     = RegNext(bypassSRT, false.B)
  val dividendInputReg = RegEnable(input.bits.a.asUInt, 0.U(32.W), input.fire)

  // SRT16 recurrence module input
  divModule.input.bits.dividend := Mux(fractEn,fractDividendIn, abs.io.aOut << leftShiftWidthDividend)
  divModule.input.bits.divider  := Mux(fractEn, fractDivisorIn, abs.io.bOut << leftShiftWidthDivisor)
  divModule.input.bits.counter  := Mux(fractEn, 8.U, counter)

  // if dividezero or biggerdivisor, bypass SRT
  divModule.input.valid := input.valid && !(bypassSRT && !fractEn) && !( !normalCaseDiv && fractEn) && !sqrtEn
  input.ready := divModule.input.ready

  // calculate quotient and remainder in ABS
  val quotientAbs  = Wire(UInt(32.W))
  val remainderAbs = Wire(UInt(32.W))
  quotientAbs  := divModule.output.bits.quotient
  remainderAbs := divModule.output.bits.reminder >> zeroHeadDivisorSRT(4, 0)

  val intQuotient  = Wire(UInt(32.W))
  val intRemainder = Wire(UInt(32.W))
  val intResult    = Wire(UInt(32.W))

  /** divInteger result collect
    *
    * when divisor equals to zero, the quotient has all bits set and the remainder equals the dividend
    */
  intQuotient := Mux(
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

  intResult := Mux(remReg, intRemainder, intQuotient)

  // -------------------- FP result collect -----------------------------------------
  val needRightShift = !divModule.output.bits.quotient(27)
  // collect sqrt result
  val sigPlusSqrt = SqrtModule.output.bits.result(24, 1) ## (!SqrtModule.output.bits.zeroRemainder || SqrtModule.output.bits.result(0))

  val sigPlusDiv = Mux(needRightShift,
    divModule.output.bits.quotient(calWidth - 3, calWidth - sigWidth - 2) ## divModule.output.bits.reminder.orR,
    divModule.output.bits.quotient(calWidth - 2, calWidth - sigWidth - 1) ## divModule.output.bits.reminder.orR
  )

  // exp logic
  val expStoreNext, expToRound = Wire(UInt((expWidth + 2).W))

  /** expStore
    *
    * output is 10bits SInt
    *
    * for sqrt
    * expForSqrt(7,0) effective is 8bits, MSB is sign
    * extends 2 sign bit in MSB
    * expStoreNext = 10bits
    *
    * todo define it format, important
    */
  expStoreNext := Mux(input.bits.sqrt,
    Cat(expForSqrt(7), expForSqrt(7), expForSqrt(7, 0)),
    (rawA.sExp - rawB.sExp).asUInt)
  val expStore = RegEnable(expStoreNext, 0.U((expWidth + 2).W), input.fire)
  expToRound := Mux(opSqrtReg, expStore, expStore - needRightShift)

  val sigPlus = Mux(opSqrtReg, sigPlusSqrt, sigPlusDiv)

  val roundresult = RoundingUnit(
    signReg,
    expToRound.asSInt,
    sigPlus,
    rmReg,
    invalidExec,
    infinitExec,
    isNaNReg,
    isInfReg,
    isZeroReg)


  output.valid := divModule.output.valid | bypassSRTReg | SqrtModule.output.valid | fastValid

  output.bits.quotient := intQuotient
  output.bits.reminder := intRemainder
  output.bits.result   := Mux(fractEnReg, roundresult(0), intResult)
  output.bits.exceptionFlags := roundresult(1)
}
