package float

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

/** RoundingUnit for all cases
  *
  * functions
  * {{{
  *   add bias to exp
  *   deal with exceptions and produce flags
  *   do rounding
  *   construct FP32 output if result is subnormal
  * }}}
  *
  * exp: 10bits SInt, MSB is sign
  * sig: 23bits UInt
  */
class RoundingUnit extends Module{
  val input = IO(Input(new RoundingInput))
  val output = IO(Output(new Bundle{
    val data = UInt(32.W)
    val exceptionFlags = Output(Bits(5.W))
  }))

  val roundingMdoe = UIntToOH(input.roundingMode)
  val rmRNE = roundingMdoe(0)
  val rmRTZ = roundingMdoe(1)
  val rmRDN = roundingMdoe(2)
  val rmRUP = roundingMdoe(3)
  val rmRMM = roundingMdoe(4)

  val commonOverflow  = Wire(Bool())
  val commonUnderflow = Wire(Bool())
  val commonInexact   = Wire(Bool())

  val sigIncr      = Wire(Bool())
  val commonExpOut = Wire(UInt(8.W))
  val commonSigOut = Wire(UInt(23.W))
  val sigAfterInc  = Wire(UInt(27.W))
  val subSigOut    = Wire(UInt(23.W))
  val subExpOut    = Wire(UInt(8.W))

  val allSig = Wire(UInt(25.W))
  allSig := input.sigPlus

  // subnormal control

  // B = A + 126 : if B<0 =>  A <= -127 => A is subnormal
  val expSubnorm = (input.exp + 126.S(10.W))
  // for non subnormal case, Dist = 0
  val subnormDist = Mux(commonUnderflow, -expSubnorm, 0.S(10.W))
  // todo why we have this case? IN IEEE754 or definded in Hardfloat?

  /** restore the hidden 1 */
  val adjustedSig = Cat(1.U(1.W), allSig)

  // rounding logic
  val allMask = ((-1).S(31.W) << 31 >> subnormDist(5,0))(31,0)
  /** For dist > 24, all bits all be rounded
    *
    * implement a optimized logcic
    * distGT32 = subnormDist(9,5).orR
    * distIn24And31 = allMask(6,0).orR
    */
  val distGT24 = (subnormDist(9,5).orR || allMask(6,0).orR) && commonUnderflow
  /** 26bits mask selecting all bits will be rounded, considering subnormal case
    *
    * last 2 bits is rbits, always 1s
    */
  val roundMask = Mux(!distGT24, Reverse(allMask(30,7)) ## "b11".U(2.W), 0.U(26.W))
  /** mask for all bits after guard bit */
  val shiftedRoundMask = Mux(!distGT24, 0.U(1.W) ## roundMask >> 1 , BigInt(-1).S(26.W).asUInt)
  /** select the guard bit */
  val guardBitMask = ~shiftedRoundMask & roundMask
  val guardBit = (adjustedSig & guardBitMask).orR
  /** Any bit is one after guard bit => sticky bit */
  val stickyBit = (adjustedSig & shiftedRoundMask).orR
  /** Any bit is one containing guard bit */
  val anyRound = guardBit || stickyBit

  /** the last effective bit */
  val lastBitMask = (guardBitMask << 1.U)(25,0)
  val lastBit = (adjustedSig & lastBitMask ).orR

  val distEQ24 = subnormDist === 24.S
  /** 2 bits for final rounding */
  val rbits : UInt= Cat(guardBit, stickyBit)

  sigIncr := (rmRNE && (rbits.andR || (lastBit && rbits==="b10".U))) ||
    (rmRDN &&  input.sign &&  rbits.orR) ||
    (rmRUP && !input.sign &&  rbits.orR) ||
    (rmRMM && rbits(1))

  /** sig_afterInc won't cover distEQ24 */
  subSigOut := Mux(
    distGT24 || distEQ24,
    Mux(sigIncr, 1.U(23.W), 0.U(23.W)),
    (sigAfterInc >> subnormDist(4,0))(24,2))
  /** when subnormDist===1.S, there may be expInc */
  subExpOut := sigAfterInc(26)  && (!commonUnderflow || subnormDist === 1.S )

  /** conforms to last bit position */
  val sigIncrement = Mux(sigIncr, lastBitMask, 0.U(26.W))
  sigAfterInc := adjustedSig +& sigIncrement

  /** Exceptions output */
  val isNaNOut = input.invalidExc || input.isNaN
  val notNaNIsSpecialInfOut = (input.infiniteExc || input.isInf) && (!input.invalidExc) && (!input.isNaN)
  val notNaNIsZero = input.isZero && !isNaNOut
  val commonCase = !isNaNOut && !notNaNIsSpecialInfOut && !input.isZero

  val overflow  = commonCase && commonOverflow
  val underflow = commonCase && commonUnderflow && commonInexact
  val inexact   = overflow || (commonCase && commonInexact)

  val overflowSele = rmRDN ## rmRUP ## rmRTZ ## (rmRNE || rmRMM)

  val common_infiniteOut = Mux1H(
    Seq(
      overflowSele(0) -> Cat(input.sign, "h7F800000".U(31.W)),
      overflowSele(1) -> Cat(input.sign, "h7F7FFFFF".U(31.W)),
      overflowSele(2) -> Mux(input.sign, "hFF7FFFFF".U(32.W), "h7F800000".U(32.W)),
      overflowSele(3) -> Mux(input.sign, "hFF800000".U(32.W), "h7F7FFFFF".U(32.W)),
    )
  )
  /** qNaN in Spike */
  val quietNaN = "h7FC00000".U

  val infiniteOut = Cat(input.sign, "h7F800000".U)
  val zeroOut = Cat(input.sign, 0.U(31.W))
  val outSele1H = commonCase ## notNaNIsSpecialInfOut ## isNaNOut ## notNaNIsZero

  /** common_overflow = input.exp > 127.S
    *
    * @todo opt it using hardfloat methods?
    */
  commonOverflow  := input.exp(8,7).orR && !input.exp(9)
  commonUnderflow := expSubnorm(9)
  commonInexact   := anyRound

  commonSigOut := sigAfterInc(24,2)
  commonExpOut := ((input.exp + 127.S)(7,0) + subExpOut).asUInt

  val commonOut = Mux(commonOverflow, common_infiniteOut,
    Mux(commonUnderflow, input.sign ## subExpOut ## subSigOut,
      input.sign ## commonExpOut ## commonSigOut))

  output.data := Mux1H(Seq(
    outSele1H(0) -> zeroOut,
    outSele1H(1) -> quietNaN,
    outSele1H(2) -> infiniteOut,
    outSele1H(3) -> commonOut)
  )

  output.exceptionFlags := input.invalidExc ## input.infiniteExc ## overflow ## underflow ## inexact

}

object RoundingUnit {
  def apply(sign: Bool, exp: SInt, sigPlus: UInt, rmode: UInt, invalidExc: Bool, infiniteExc: Bool, isNaN: Bool, isInf: Bool, isZero: Bool): Vec[UInt] = {
    val rounder = Module(new RoundingUnit)
    rounder.input.sign := sign
    rounder.input.sigPlus := sigPlus
    rounder.input.exp := exp
    rounder.input.roundingMode := rmode
    rounder.input.invalidExc := invalidExc
    rounder.input.infiniteExc := infiniteExc
    rounder.input.isInf := isInf
    rounder.input.isZero := isZero
    rounder.input.isNaN := isNaN
    VecInit(rounder.output.data, rounder.output.exceptionFlags)
  }
}

class RoundingInput extends Bundle{
  val invalidExc = Bool() // overrides 'infiniteExc' and 'in'
  val infiniteExc = Bool() // overrides 'in' except for 'in.sign'
  val isInf = Bool()
  val isZero = Bool()
  val isNaN = Bool()
  /** 23bits + 2 bits for rounding */
  val sigPlus = UInt(25.W)
  /** true exp with sign
    *
    * why need 10bit:
    * for div: ExpMaxMag = 255 - (0+22) = 277
    * */
  val exp = SInt(10.W)
  val sign = Bool()
  val roundingMode = UInt(5.W)
}


