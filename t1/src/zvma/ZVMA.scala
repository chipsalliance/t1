package org.chipsalliance.t1.rtl.zvma

import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
import chisel3._
import org.chipsalliance.t1.rtl.lsu.DataToZVMA

object ZVMAParameter {
  implicit def rw: upickle.default.ReadWriter[ZVMAParameter] = upickle.default.macroRW
}

case class ZVMAParameter(
  vlen:             Int,
  dlen:             Int,
  elen:             Int,
  TE:               Int,
  matrixAluRowSize: Int,
  matrixAluColSize: Int)
    extends SerializableModuleParameter {
  val tmWidth: Int = log2Ceil(TE + 1)
  val tnWidth: Int = log2Ceil(vlen + 1)

  // The minimum execution unit is a 2 * 2 square matrix
  val aluRowSize = matrixAluRowSize
  val aluColSize = matrixAluColSize

  val dataIndexBit: Int = log2Ceil(vlen * 8 / dlen + 1)

  // source buffer param calculate
  val aluSizeVec: Seq[Int] = Seq(aluRowSize, aluColSize)

  val subArrayBufferDepth = 8
  // Should be a constant
  // If an instruction col index is fixed (such as mv), a higher bank may be required.
  val subArrayRamBank     = 2

  // The minimum unit is 32 * 2 * 2, so ram width is 32 * 4
  val ramDepth: Int = TE * TE * 32 * 4 / (32 * 4 * aluRowSize * aluColSize * subArrayRamBank)
}

class ZVMCsrInterface(parameter: ZVMAParameter) extends Bundle {
  val sew: UInt = UInt(2.W)
  // TEW = SEW * TWIDEN
  val tew = UInt(3.W)
  // tk can hold values from 0-4, inclusive.
  val tk  = UInt(3.W)
  // tm can hold values from 0-TE, inclusive.
  val tm  = UInt(parameter.tmWidth.W)
  val tn  = UInt(parameter.tnWidth.W)
}

class ZVMAInstRequest(parameter: ZVMAParameter) extends Bundle {
  val instruction: UInt = UInt(32.W)
  // mv use rs1, load/store use rs2
  val scalaSource: UInt = UInt(32.W)
  val csr = new ZVMCsrInterface(parameter)
}

class ZVMAInterface(parameter: ZVMAParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Bool())
  val request:     ValidIO[ZVMAInstRequest] = Flipped(Valid(new ZVMAInstRequest(parameter)))
  val dataFromLSU: DecoupledIO[DataToZVMA]  = Flipped(Decoupled(new DataToZVMA(parameter.dlen)))
  val dataToLSU:   DecoupledIO[UInt]        = Decoupled(UInt(parameter.dlen.W))
  val idle:        Bool                     = Output(Bool())
}

class ZVMA(val parameter: ZVMAParameter)
    extends FixedIOExtModule(new ZVMAInterface(parameter))
    with SerializableModule[ZVMAParameter] {
  val paramDir = java.nio.file.Paths.get("zaozi-params")
  java.nio.file.Files.createDirectories(paramDir)
  java.nio.file.Files.write(
    paramDir.resolve("ZVMA.json"),
    upickle.default.write(parameter).getBytes(java.nio.charset.StandardCharsets.UTF_8)
  )
}
