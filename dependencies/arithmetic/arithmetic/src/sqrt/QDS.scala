package sqrt

import chisel3._
import chisel3.util.BitPat
import chisel3.util.BitPat.bitPatToUInt
import chisel3.util.experimental.decode.TruthTable
import utils.{extend, sIntToBitPat}

/** Result-Digit Selection
  *
  * @param rWidth y Truncate width
  * @param ohWidth quotient width
  * @param partialDividerWidth equals to dTruncatedWidth - 1
  */
class QDS(rWidth: Int, ohWidth: Int, partialDividerWidth: Int, a: Int) extends Module {
  // IO
  val input = IO(Input(new QDSInput(rWidth, partialDividerWidth)))
  val output = IO(Output(new QDSOutput(ohWidth)))

  // from P269 in <Digital Arithmetic> : /16， should have got from SRTTable.
  // val qSelTable = Array(
  //   Array(12, 4, -4, -13),
  //   Array(14, 4, -5, -14),
  //   Array(16, 4, -6, -16),
  //   Array(16, 4, -6, -17),
  //   Array(18, 6, -6, -18),
  //   Array(20, 6, -8, -20),
  //   Array(20, 8, -8, -22),
  //   Array(22, 8, -8, -23)/16
  //
  //   @todo calculate the table
  // )
      val selectRom: Vec[Vec[UInt]] = VecInit(
        VecInit("b111_0100".U, "b111_1100".U, "b000_0100".U, "b000_1101".U),
        VecInit("b111_0010".U, "b111_1100".U, "b000_0101".U, "b000_1110".U),
        VecInit("b111_0000".U, "b111_1100".U, "b000_0110".U, "b001_0000".U),
        VecInit("b111_0000".U, "b111_1100".U, "b000_0110".U, "b001_0001".U),
        VecInit("b110_1110".U, "b111_1010".U, "b000_0110".U, "b001_0010".U),
        VecInit("b110_1100".U, "b111_1010".U, "b000_1000".U, "b001_0100".U),
        VecInit("b110_1100".U, "b111_1000".U, "b000_1000".U, "b001_0110".U),
        VecInit("b110_1010".U, "b111_1000".U, "b000_1000".U, "b001_0111".U)
      )

  val columnSelect = input.partialDivider
  val adderWidth = rWidth + 1

  /** 3 integer bits, 4 fractional bits */
  val yTruncate: UInt = input.partialReminderCarry + input.partialReminderSum

  /** the selection constant vector */
  val mkVec = selectRom(columnSelect)

  /** add [[yTruncate]] with all mk, use decoder to find its location */
  val selectPoints = VecInit(mkVec.map { mk =>
    (extend(yTruncate, adderWidth).asUInt
      + extend(mk, adderWidth).asUInt).head(1)
  }).asUInt

  /** finds the first one in [[selectPoints]] to select the result(in oneHot)
    *
    * use decoder or findFirstOne here, prefer decoder, the decoder only for srt4
    */
  output.selectedQuotientOH := chisel3.util.experimental.decode.decoder(
    selectPoints,
    a match {
      case 2 =>
        TruthTable(
          Seq(
            BitPat("b???0") -> BitPat("b10000"), //2
            BitPat("b??01") -> BitPat("b01000"), //1
            BitPat("b?011") -> BitPat("b00100"), //0
            BitPat("b0111") -> BitPat("b00010") //-1
          ),
          BitPat("b00001") //-2
        )
    }
  )
}

object QDS {
  def apply(
             rWidth:               Int,
             ohWidth:              Int,
             partialDividerWidth:  Int,
             a:                    Int
           )(partialReminderSum:   UInt,
             partialReminderCarry: UInt,
             partialDivider:       UInt
           ): UInt = {
    val m = Module(new QDS(rWidth, ohWidth, partialDividerWidth, a))
    m.input.partialReminderSum := partialReminderSum
    m.input.partialReminderCarry := partialReminderCarry
    m.input.partialDivider := partialDivider
    m.output.selectedQuotientOH
  }
}
