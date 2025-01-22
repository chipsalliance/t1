package sqrt

import chisel3._
import chisel3.util._
import division.srt.srt4.OTF

/** SquareRoot
  *
  * all example xxx assumes inputWidth = 8
  *
  * {{{
  * oprand = 0.1xxxxx > 1/2  , input.bits.oprand  = 1xxxx
  * oprand = 0.01xxxxx > 1/4 , input.bits.oprand  = 01xxxx
  * result = 0.1xxxxx > 1/2 , output.bits.result = 1xxxxx
  *
  * }}}
  *
  * csa width = partialresult width : wlen = inputwidth + 2
  * csa width(formation width) : wlen
  * resultOrigin and Minus: outputWidth
  *
  * outputWidth must <= inputWidth +2 or we can't get exact FormationFinal
  *
  * @note inputWidth mod 2 ==0
  *
  * @example if oprand = .10110000, input.bits.oprand shoule be 10110000
  *
  * @param radixLog2 SRT radix log2
  * @param a Redundent system
  * @param inputWidth   width for input
  * @param outputWidth  width for result ,need to be inputwidth + 2
  */
class SquareRoot(
  radixLog2:   Int,
  a:           Int,
  inputWidth:  Int,
  outputWidth: Int)
    extends Module {
  val input = IO(Flipped(DecoupledIO(new SquareRootInput(inputWidth: Int))))
  val output = IO(ValidIO(new SquareRootOutput(outputWidth)))

  /** width for partial result  */
  val wlen = inputWidth + 2

  /** W[j] = xx.xxxxxxxx
    *
    * width = 2 + inputwidth
    */
  val partialCarryNext, partialSumNext = Wire(UInt(wlen.W))
  /** S[j] = .xxxxxxxx
    *
    * effective bits number depends on counter, 2n+1
    *
    * effective length grows from LSB and depends on j
    * */
  val resultOriginNext, resultMinusOneNext = Wire(UInt((outputWidth).W))
  val counterNext = Wire(UInt(log2Ceil(outputWidth).W))

  // Control logic
  val isLastCycle, enable: Bool = Wire(Bool())
  val occupiedNext = Wire(Bool())
  val occupied = RegNext(occupiedNext, false.B)
  val counter = RegEnable(counterNext, 0.U(log2Ceil(outputWidth).W), enable)

  occupiedNext := input.fire || (!isLastCycle && occupied)
  isLastCycle  := counter === (outputWidth/2).U
  input.ready  := !occupied
  enable       := input.fire || !isLastCycle
  output.valid := occupied && isLastCycle

  /** Data REG */
  val resultOrigin       = RegEnable(resultOriginNext,   0.U((outputWidth).W), enable)
  val resultMinusOne     = RegEnable(resultMinusOneNext, 0.U((outputWidth).W), enable)
  val partialCarry       = RegEnable(partialCarryNext,   0.U(wlen.W),          enable)
  val partialSum         = RegEnable(partialSumNext,     0.U(wlen.W),          enable)

  /** rW[j] = xxxx.xxxxxxxx
    *
    * first 7 bits truncated for QDS
    */
  val shiftSum, shiftCarry = Wire(UInt((wlen+2).W))
  shiftSum   := partialSum   << 2
  shiftCarry := partialCarry << 2

  /** todo later parameterize it */
  val rtzYWidth = 7
  val rtzSWidth = 4
  val ohWidth = 5

  /** S[j] = x.xxxxxxxx
    * width = outwidth + 1
    *
    * transform to fixpoint representation for truncation
    * shift effective bits(2j+1)  to MSB
    */
  val resultOriginRestore = (resultOrigin << outputWidth.U >> (counter << 1).asUInt)(outputWidth, 0)

  /** truncated y for QDS */
  val resultForQDS = Mux(
    counter === 0.U,
    "b101".U,
    Mux(resultOriginRestore(outputWidth), "b111".U, resultOriginRestore(outputWidth - 2, outputWidth - 4))
  )

  val selectedQuotientOH: UInt =
    QDS(rtzYWidth, ohWidth, rtzSWidth - 1, a)(
      shiftSum.head(rtzYWidth),
      shiftCarry.head(rtzYWidth),
      resultForQDS //.1********* -> 1*** -> ***
    )

  /** On-The-Fly conversion */
  val otf = OTF(radixLog2, outputWidth, ohWidth, a)(resultOrigin, resultMinusOne, selectedQuotientOH)

  /** effective bits : LSB 2j+1+4 = 2j + 5 */
  val formationForIter = Mux1H(
    Seq(
      selectedQuotientOH(0) -> (resultMinusOne << 4 | "b1100".U),
      selectedQuotientOH(1) -> (resultMinusOne << 3 | "b111".U),
      selectedQuotientOH(2) -> 0.U,
      selectedQuotientOH(3) -> (~resultOrigin << 3  | "b111".U),
      selectedQuotientOH(4) -> (~resultOrigin << 4  | "b1100".U)
    )
  )

  /** Formation for csa
    *
    * to construct formationFinal
    * shift formationIter effective bits to MSB
    * need to shift wlen + 1 - (2j+5)
    *
    * @todo width fixed to wlen + 1, prove it
    */
  val formationFinal = Wire(UInt((wlen + 1).W))
  formationFinal := formationForIter << (wlen - 4) >> (counter << 1)

  /** csa width : wlen */
  val csa: Vec[UInt] = addition.csa.c32(
    VecInit(
      shiftSum(inputWidth + 1, 0),
      shiftCarry(inputWidth + 1, 0),
      formationFinal(inputWidth + 1, 0)
    )
  )

  /** @todo opt SZ logic */
  val remainderFinal = partialSum + partialCarry
  val needCorrect: Bool = remainderFinal(wlen - 1).asBool

  /** w[0] = oprand - 1.U */
  val initSum = Cat("b11".U, input.bits.operand)

  /** init S[0] = 1 */
  resultOriginNext       := Mux(input.fire, 1.U, otf(0))
  resultMinusOneNext     := Mux(input.fire, 0.U, otf(1))
  partialSumNext         := Mux(input.fire, initSum, csa(1))
  partialCarryNext       := Mux(input.fire, 0.U, csa(0) << 1)
  counterNext            := Mux(input.fire, 0.U, counter + 1.U)

  output.bits.result := Mux(needCorrect, resultMinusOne, resultOrigin)
  output.bits.zeroRemainder := !remainderFinal.orR
}
