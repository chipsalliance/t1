package org.chipsalliance.t1.rtl.zvma

import chisel3.experimental.SerializableModule
import chisel3.util._
import chisel3._

class ProcessInterface(parameter: ZVMAParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Bool())
  val request:  ValidIO[ZVMAExecute] = Flipped(Valid(new ZVMAExecute(parameter)))
  val response: DecoupledIO[UInt]    = Decoupled(UInt((parameter.elen * 2).W))
  val release:  Bool                 = Output(Bool())
}

class ZVMAProcessingElement(val parameter: ZVMAParameter)
    extends FixedIOExtModule(new ProcessInterface(parameter))
    with SerializableModule[ZVMAParameter] {
  val paramDir = java.nio.file.Paths.get("zaozi-params")
  java.nio.file.Files.createDirectories(paramDir)
  java.nio.file.Files.write(
    paramDir.resolve("ZVMAProcessingElement.json"),
    upickle.default.write(parameter).getBytes(java.nio.charset.StandardCharsets.UTF_8)
  )
}
