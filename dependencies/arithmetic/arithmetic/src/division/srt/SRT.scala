package division.srt

import division.srt.srt4._
import division.srt.srt8._
import division.srt.srt16._
import chisel3._
import chisel3.util.{DecoupledIO, ValidIO}

class SRT(
  dividendWidth:  Int,
  dividerWidth:   Int,
  n:              Int, // the longest width
  radixLog2:      Int = 2,
  a:              Int = 2,
  dTruncateWidth: Int = 4,
  rTruncateWidth: Int = 4)
    extends Module {
//  val x = (radixLog2, a, dTruncateWidth)
//  val tips = x match {
//    case (2,2,4) => require(rTruncateWidth >= 4, "rTruncateWidth need >= 4")
//    case (2,2,5) => require(rTruncateWidth >= 4, "rTruncateWidth need >= 4")
//    case (2,2,6) => require(rTruncateWidth >= 4, "rTruncateWidth need >= 4")
//
//    case (3,4,6) => require(rTruncateWidth >= 7, "rTruncateWidth need >= 7")
//    case (3,4,7) => require(rTruncateWidth >= 6, "rTruncateWidth need >= 6")
//
//    case (3,5,5) => require(rTruncateWidth >= 5, "rTruncateWidth need >= 5")
//    case (3,5,6) => require(rTruncateWidth >= 4, "rTruncateWidth need >= 4")
//
//    case (3,6,4) => require(rTruncateWidth >= 6, "rTruncateWidth need >= 6")
//    case (3,6,5) => require(rTruncateWidth >= 4, "rTruncateWidth need >= 4")
//
//    case (3,7,4) => require(rTruncateWidth >= 4, "rTruncateWidth need >= 4")
//    case (3,7,5) => require(rTruncateWidth >= 3, "rTruncateWidth need >= 3")
//
//    case (4,2,4) => require(rTruncateWidth >= 4, "rTruncateWidth need >= 4")
//    case (4,2,5) => require(rTruncateWidth >= 4, "rTruncateWidth need >= 4")
//    case (4,2,6) => require(rTruncateWidth >= 4, "rTruncateWidth need >= 4")
//
//    case _       => println("this srt is not supported")
//  }

  val input = IO(Flipped(DecoupledIO(new SRTInput(dividendWidth, dividerWidth, n, radixLog2))))
  val output = IO(ValidIO(new SRTOutput(dividerWidth, dividendWidth)))

//   select radix
  if (radixLog2 == 2) { // SRT4
    val srt = Module(new SRT4(dividendWidth, dividerWidth, n, radixLog2, a, dTruncateWidth, rTruncateWidth))
    srt.input <> input
    output <> srt.output
  } else if (radixLog2 == 3) { // SRT8
    val srt = Module(new SRT8(dividendWidth, dividerWidth, n, radixLog2, a, dTruncateWidth, rTruncateWidth))
    srt.input <> input
    output <> srt.output
  } else if (radixLog2 == 4) { //SRT16
    val srt = Module(new SRT16(dividendWidth, dividerWidth, n, radixLog2 >> 1, a, dTruncateWidth, rTruncateWidth))
    srt.input <> input
    output <> srt.output
  }

//  val srt = radixLog2 match {
//    case 2 => Module(new SRT4(dividendWidth, dividerWidth, n, radixLog2, a, dTruncateWidth, rTruncateWidth))
//    case 3 => Module(new SRT8(dividendWidth, dividerWidth, n, radixLog2, a, dTruncateWidth, rTruncateWidth))
//    case 4 => Module(new SRT16(dividendWidth, dividerWidth, n, radixLog2 >> 1, a, dTruncateWidth, rTruncateWidth))
//  }
//  srt.input <> input
//  output <> srt.output
}
