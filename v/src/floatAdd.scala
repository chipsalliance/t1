package v

import chisel3._
import chisel3.util._
import hardfloat._

class floatAdd extends Module{
  val expWidth = 8
  val sigWidth = 24
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