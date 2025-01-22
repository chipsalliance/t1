package sqrt

import chisel3._
import chisel3.util._
import division.srt.{OTFInput, OTFOutput}
import division.srt.srt16.OTF

class SqrtIter(
                radixLog2:   Int,
                a:           Int,
                iterWidth:  Int,
                outputWidth: Int)
  extends Module {
  /** todo later parameterize it */
  val rtzYWidth = 7
  val rtzSWidth = 4
  val ohWidth = 5

  val input = IO(Flipped(DecoupledIO(new SqrtIterIn(iterWidth))))
  val resultOutput = IO(ValidIO(new SquareRootOutput(outputWidth)))
  val output = IO(Output(new SqrtIterOut(iterWidth)))
  val reqOTF = IO(Output(new OTFInput(outputWidth, ohWidth)))
  val respOTF = IO(Input(new OTFOutput(outputWidth)))

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
  resultOutput.valid := occupied && isLastCycle

  /** Data REG */
  val resultOrigin       = RegEnable(resultOriginNext,   0.U((outputWidth).W), enable)
  val resultMinusOne     = RegEnable(resultMinusOneNext, 0.U((outputWidth).W), enable)
  val partialCarry       = input.bits.partialCarry
  val partialSum         = input.bits.partialSum

  /** rW[j] = xxxx.xxxxxxxx
    *
    * first 7 bits truncated for QDS
    */
  val shiftSum, shiftCarry = Wire(UInt((iterWidth+2).W))
  shiftSum   := partialSum   << 2
  shiftCarry := partialCarry << 2

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
  val formationFinal = Wire(UInt((iterWidth + 1).W))
  formationFinal := formationForIter << (iterWidth - 4) >> (counter << 1)

  /** csa width : wlen */
  val csa: Vec[UInt] = addition.csa.c32(
    VecInit(
      shiftSum(iterWidth - 1, 0),
      shiftCarry(iterWidth - 1, 0),
      formationFinal(iterWidth - 1, 0)
    )
  )

  /** @todo opt SZ logic */
  val remainderFinal = partialSum + partialCarry
  val needCorrect: Bool = remainderFinal(iterWidth - 1).asBool

  /** init S[0] = 1 */
  resultOriginNext       := Mux(input.fire, 1.U, respOTF.quotient)
  resultMinusOneNext     := Mux(input.fire, 0.U, respOTF.quotientMinusOne)
  counterNext            := Mux(input.fire, 0.U, counter + 1.U)

  resultOutput.bits.result := Mux(needCorrect, resultMinusOne, resultOrigin)
  resultOutput.bits.zeroRemainder := !remainderFinal.orR

  reqOTF.quotient := resultOrigin
  reqOTF.quotientMinusOne := resultMinusOne
  reqOTF.selectedQuotientOH := selectedQuotientOH

  output.partialSum := csa(1)
  output.partialCarry := csa(0) << 1
  output.isLastCycle := isLastCycle
}

class SqrtSplited(
                   radixLog2:   Int,
                   a:           Int,
                   inputWidth:  Int,
                   outputWidth: Int) extends Module{
  val input = IO(Flipped(DecoupledIO(new SquareRootInput(inputWidth: Int))))
  val output = IO(ValidIO(new SquareRootOutput(outputWidth)))
  /** width for partial result */
  val iterWidth = inputWidth + 2
  val ohWidth = 5

  val iter = Module(new SqrtIter(radixLog2, a, iterWidth, outputWidth))
  val otf = OTF(radixLog2, outputWidth, ohWidth)(iter.reqOTF.quotient, iter.reqOTF.quotientMinusOne, iter.reqOTF.selectedQuotientOH)

  val partialCarryNext, partialSumNext = Wire(UInt(iterWidth.W))
  val enable = input.fire || !iter.output.isLastCycle

  /** w[0] = oprand - 1.U */
  val initSum = Cat("b11".U, input.bits.operand)
  partialSumNext   := Mux(input.fire, initSum, iter.output.partialSum)
  partialCarryNext := Mux(input.fire, 0.U, iter.output.partialCarry)

  val partialCarry = RegEnable(partialCarryNext, 0.U(iterWidth.W), enable)
  val partialSum   = RegEnable(partialSumNext, 0.U(iterWidth.W), enable)

  input.ready := iter.input.ready

  iter.input.valid := input.valid
  iter.input.bits.partialCarry := partialCarry
  iter.input.bits.partialSum   := partialSum

  iter.respOTF.quotient := otf(0)
  iter.respOTF.quotientMinusOne := otf(1)

  output := iter.resultOutput

}