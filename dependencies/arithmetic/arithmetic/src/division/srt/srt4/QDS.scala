package division.srt.srt4

import division.srt._
import chisel3._
import chisel3.util.BitPat
import chisel3.util.BitPat.bitPatToUInt
import chisel3.util.experimental.decode.TruthTable
import utils.{extend, sIntToBitPat}

/** quotient divisor select
  *
  * @param rWidth y Truncate width
  * @param ohWidth quotient width
  * @param partialDividerWidth equals to dTruncatedWidth - 1
  * @param tables QDS table
  */
class QDS(rWidth: Int, ohWidth: Int, partialDividerWidth: Int, tables: Seq[Seq[Int]], a: Int) extends Module {
  // IO
  val input = IO(Input(new QDSInput(rWidth, partialDividerWidth)))
  val output = IO(Output(new QDSOutput(ohWidth)))

  // from P269 in <Digital Arithmetic> : /16， should have got from SRTTable.
  // val qSelTable = Array(
  //   Array(12, 4, -4, -13),
  //   Array(14, 4, -6, -15),
  //   Array(15, 4, -6, -16),
  //   Array(16, 4, -6, -18),
  //   Array(18, 6, -8, -20),
  //   Array(20, 6, -8, -20),
  //   Array(20, 8, -8, -22),
  //   Array(24, 8, -8, -24)/16
  // )
  //  val selectRom: Vec[Vec[UInt]] = VecInit(
  //    VecInit("b111_0100".U, "b111_1100".U, "b000_0100".U, "b000_1101".U),
  //    VecInit("b111_0010".U, "b111_1100".U, "b000_0110".U, "b000_1111".U),
  //    VecInit("b111_0001".U, "b111_1100".U, "b000_0110".U, "b001_0000".U),
  //    VecInit("b111_0000".U, "b111_1100".U, "b000_0110".U, "b001_0010".U),
  //    VecInit("b110_1110".U, "b111_1010".U, "b000_1000".U, "b001_0100".U),
  //    VecInit("b110_1100".U, "b111_1010".U, "b000_1000".U, "b001_0100".U),
  //    VecInit("b110_1100".U, "b111_1000".U, "b000_1000".U, "b001_0110".U),
  //    VecInit("b110_1000".U, "b111_1000".U, "b000_1000".U, "b001_1000".U)
  //  )

  // get from SRTTable.
  lazy val selectRom = VecInit(tables.map {
    case x =>
      VecInit(x.map {
        case x => bitPatToUInt(sIntToBitPat(-x, rWidth))
      })
  })

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

  // decoder or findFirstOne here, prefer decoder, the decoder only for srt4
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
      case 3 =>
        TruthTable(
          Seq( // 2 0 -2 1 0 -1
            BitPat("b??_???0") -> BitPat("b100_100"), //3 = 2 + 1
            BitPat("b??_??01") -> BitPat("b100_010"), //2 = 2 + 0
            BitPat("b??_?011") -> BitPat("b010_100"), //1 = 0 + 1
            BitPat("b??_0111") -> BitPat("b010_010"), //0 = 0 + 0
            BitPat("b?0_1111") -> BitPat("b010_001"), //-1 = 0 + -1
            BitPat("b01_1111") -> BitPat("b001_010") //-2 = -2 + 0
          ),
          BitPat("b001_001") //-3 = -2 + -1
        )
    }
  )
}

object QDS {
  def apply(
    rWidth:               Int,
    ohWidth:              Int,
    partialDividerWidth:  Int,
    tables:               Seq[Seq[Int]],
    a:                    Int
  )(partialReminderSum:   UInt,
    partialReminderCarry: UInt,
    partialDivider:       UInt
  ): UInt = {
    val m = Module(new QDS(rWidth, ohWidth, partialDividerWidth, tables, a))
    m.input.partialReminderSum := partialReminderSum
    m.input.partialReminderCarry := partialReminderCarry
    m.input.partialDivider := partialDivider
    m.output.selectedQuotientOH
  }
}
