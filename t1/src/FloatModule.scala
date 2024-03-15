// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode.TruthTable
import hardfloat._

class FloatAdder(expWidth: Int, sigWidth:Int) extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt((expWidth + sigWidth).W))
    val b = Input(UInt((expWidth + sigWidth).W))
    val roundingMode = Input(UInt(3.W))
    val out = Output(UInt((expWidth + sigWidth).W))
    val exceptionFlags = Output(UInt(5.W))
  })
  val addRecFN = Module(new AddRecFN(expWidth, sigWidth))
  addRecFN.io.subOp := false.B
  addRecFN.io.a := recFNFromFN(expWidth, sigWidth, io.a)
  addRecFN.io.b := recFNFromFN(expWidth, sigWidth, io.b)
  addRecFN.io.roundingMode := io.roundingMode
  addRecFN.io.detectTininess := false.B

  io.out := fNFromRecFN(8, 24, addRecFN.io.out)
  io.exceptionFlags := addRecFN.io.exceptionFlags
}

/** float compare module
  *
  * isMax = true  => max
  * isMax = false => min
  *
  * perform a quiet comparing in IEEE-754
  */
class FloatCompare(expWidth: Int, sigWidth:Int) extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt((expWidth + sigWidth).W))
    val b = Input(UInt((expWidth + sigWidth).W))
    val isMax = Input(Bool())
    val out = Output(UInt((expWidth + sigWidth).W))
    val exceptionFlags = Output(UInt(5.W))
  })

  val rec0 = recFNFromFN(expWidth, sigWidth, io.a)
  val rec1 = recFNFromFN(expWidth, sigWidth, io.b)

  val raw0 = rawFloatFromRecFN(8, 24, rec0)
  val raw1 = rawFloatFromRecFN(8, 24, rec1)

  val compareModule = Module(new CompareRecFN(8, 24))
  compareModule.io.a := rec0
  compareModule.io.b := rec1
  compareModule.io.signaling := false.B

  val oneNaN = raw0.isNaN ^ raw1.isNaN
  val hasNaNResult = Mux(oneNaN,
    Mux(raw0.isNaN, io.b, io.a),
    "x7fc00000".U
  )
  val hasNaN = raw0.isNaN || raw1.isNaN

  val noNaNResult = Mux((io.isMax && compareModule.io.gt) || (!io.isMax && compareModule.io.lt), io.a, io.b)

  io.out := Mux(hasNaN, hasNaNResult, noNaNResult)
  io.exceptionFlags := compareModule.io.exceptionFlags
}

class Rec7Fn extends Module {
  val in = IO(Input(new Bundle {
    val data = UInt(32.W)
    val classifyIn = UInt(10.W)
    val roundingMode = UInt(3.W)
  }))
  val out = IO(Output(new Bundle {
    val data = UInt(32.W)
    val exceptionFlags = UInt(5.W)
  }))

  val sign = in.data(31)
  val expIn = in.data(30, 23)
  val fractIn = in.data(22, 0)

  val inIsPositiveInf = in.classifyIn(7)
  val inIsNegativeInf = in.classifyIn(0)
  val inIsNegativeZero = in.classifyIn(3)
  val inIsPositveZero = in.classifyIn(4)
  val inIsSNaN = in.classifyIn(8)
  val inIsQNaN = in.classifyIn(9)
  val inIsSub = in.classifyIn(2) || in.classifyIn(5)
  val inIsSubMayberound = inIsSub && !in.data(22, 21).orR
  val maybeRoundToMax = in.roundingMode === 1.U ||
    ((in.roundingMode === 2.U) && !sign) ||
    ((in.roundingMode === 3.U) && sign)
  val maybyRoundToNegaInf = sign &&
    (in.roundingMode === 0.U ||
    in.roundingMode === 4.U ||
    in.roundingMode === 2.U)
  val maybyRoundToPosInf = !sign &&
    (in.roundingMode === 0.U ||
    in.roundingMode === 4.U ||
    in.roundingMode === 3.U)
  val roundAbnormalToMax     = inIsSubMayberound && maybeRoundToMax
  val roundAbnormalToNegaInf = inIsSubMayberound && maybyRoundToNegaInf
  val roundAbnormalToPosInf  = inIsSubMayberound && maybyRoundToPosInf
  val roundAbnormal = roundAbnormalToPosInf || roundAbnormalToNegaInf || roundAbnormalToMax

  val normDist = Wire(UInt(8.W))
  val normExpIn = Wire(UInt(8.W))
  val normSigIn = Wire(UInt(23.W))
  val normExpOut = Wire(UInt(8.W))
  val normSigOut = Wire(UInt(23.W))
  val sigOut = Wire(UInt(23.W))
  val expOut = Wire(UInt(8.W))

  normDist := float.countLeadingZeros(fractIn)
  normExpIn := Mux(inIsSub, -normDist, expIn)

  // todo timing issue
  normSigIn := Mux(inIsSub, fractIn << (1.U - normExpIn), fractIn)

  val rec7Decoder = Module(new Rec7LUT)
  rec7Decoder.in := normSigIn(22, 16)

  normSigOut := Cat(rec7Decoder.out, 0.U(16.W))
  normExpOut := 253.U - normExpIn

  val outIsSub = normExpOut === 0.U || normExpOut.andR
  expOut := Mux(outIsSub, 0.U, normExpOut)
  val outSubShift = Mux(normExpOut === 0.U, 1.U, 2.U)
  sigOut := Mux(outIsSub, Cat(1.U(1.W), normSigOut) >> outSubShift, normSigOut)

  out.data := Mux(inIsNegativeInf, "x80000000".U,
    Mux(inIsPositiveInf, 0.U,
      Mux(inIsNegativeZero || roundAbnormalToNegaInf, "xff800000".U,
        Mux(inIsPositveZero || roundAbnormalToPosInf , "x7f800000".U,
          Mux(inIsQNaN || inIsSNaN, "x7FC00000".U,
            Mux(roundAbnormalToMax, Cat(sign, "x7f7fffff".U),
              Cat(sign, expOut,sigOut)))))))
  out.exceptionFlags := Mux(inIsSNaN, 16.U,
    Mux(inIsPositveZero || inIsNegativeZero, 8.U,
      Mux(roundAbnormal, 5.U, 0.U)))
}

class Rsqrt7Fn extends Module {
  val in = IO(Input(new Bundle{
    val data = UInt(32.W)
    val classifyIn = UInt(10.W)
  }))
  val out = IO(Output(new Bundle {
    val data = UInt(32.W)
    val exceptionFlags = UInt(5.W)
  }))

  val sign = in.data(31)
  val expIn = in.data(30, 23)
  val fractIn = in.data(22, 0)
  val inIsSub = !expIn.orR

  val outNaN = Cat(in.classifyIn(2,0), in.classifyIn(9,8)).orR
  val outInf = in.classifyIn(3)|| in.classifyIn(4)

  val normDist = Wire(UInt(8.W))
  val normExpIn = Wire(UInt(8.W))
  val normSigIn = Wire(UInt(23.W))
  val sigOut = Wire(UInt(23.W))
  val expOut = Wire(UInt(8.W))

  normDist := float.countLeadingZeros(fractIn)
  normExpIn := Mux(inIsSub, -normDist, expIn)
  // todo timing issue
  normSigIn := Mux(inIsSub, fractIn << (1.U - normExpIn), fractIn)

  val rsqrt7Decoder = Module(new Rsqrt7LUT)
  rsqrt7Decoder.in := Cat(normExpIn(0), normSigIn(22,17))

  sigOut := Cat(rsqrt7Decoder.out, 0.U(16.W))
  expOut := (380.U - normExpIn) >> 1

  out.data := Mux(outNaN, "x7FC00000".U, Cat(sign, expOut, sigOut))
  out.exceptionFlags := Mux(outNaN, 16.U,
    Mux(outInf, 8.U, 0.U))
}

class LUT(table: Seq[Int], inputWidth: Int, outputWidth: Int) extends Module {
  val in = IO(Input(UInt(inputWidth.W)))
  val out = IO(Output(UInt(outputWidth.W)))
  out := chisel3.util.experimental.decode.decoder.espresso(in, TruthTable(table.zipWithIndex.map {
    case (data, addr) => (BitPat(addr.U(inputWidth.W)), BitPat(data.U(outputWidth.W)))
  }, BitPat.N(outputWidth)))
}

/** @note see riscv-v-spec v1.0 p65 */
class Rec7LUT extends LUT (
  Seq(127, 125, 123, 121, 119, 117, 116, 114,
    112, 110, 109, 107, 105, 104, 102, 100, 99, 97,
    96, 94, 93, 91, 90, 88, 87, 85, 84, 83, 81, 80,
    79, 77, 76, 75, 74, 72, 71, 70, 69, 68, 66, 65,
    64, 63, 62, 61, 60, 59, 58, 57, 56, 55, 54, 53,
    52, 51, 50, 49, 48, 47, 46, 45, 44, 43, 42, 41,
    40, 40, 39, 38, 37, 36, 35, 35, 34, 33, 32, 31,
    31, 30, 29, 28, 28, 27, 26, 25, 25, 24, 23, 23,
    22, 21, 21, 20, 19, 19, 18, 17, 17, 16, 15, 15,
    14, 14, 13, 12, 12, 11, 11, 10, 9, 9, 8, 8, 7,
    7, 6, 5, 5, 4, 4, 3, 3, 2, 2, 1, 1, 0),7,7)

/** @note see riscv-v-spec v1.0 p61 */
class Rsqrt7LUT extends LUT (
  Seq(52, 51, 50, 48, 47, 46, 44, 43,
    42, 41, 40, 39, 38, 36, 35, 34,
    33, 32, 31, 30, 30, 29, 28, 27,
    26, 25, 24, 23, 23, 22, 21, 20,
    19, 19, 18, 17, 16, 16, 15, 14,
    14, 13, 12, 12, 11, 10, 10, 9,
    9, 8, 7, 7, 6, 6, 5, 4,
    4, 3, 3, 2, 2, 1, 1, 0,
    127, 125, 123, 121, 119, 118, 116, 114,
    113, 111, 109, 108, 106, 105, 103, 102,
    100, 99, 97, 96, 95, 93, 92, 91,
    90, 88, 87, 86, 85, 84, 83, 82,
    80, 79, 78, 77, 76, 75, 74, 73,
    72, 71, 70, 70, 69, 68, 67, 66,
    65, 64, 63, 63, 62, 61, 60, 59,
    59, 58, 57, 56, 56, 55, 54, 53),7,7)

