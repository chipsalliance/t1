// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.instantiable

object LanePopCountParam {
  implicit def rw: upickle.default.ReadWriter[LanePopCountParam] = upickle.default.macroRW
}

case class LanePopCountParam(datapathWidth: Int) extends SerializableModuleParameter

class LanePopCountInterface(parameter: LanePopCountParam) extends Bundle {
  val clock: Clock = Input(Clock())
  val reset: Bool  = Input(Bool())
  val src:   UInt  = Input(UInt(parameter.datapathWidth.W))
  val resp:  UInt  = Output(UInt(parameter.datapathWidth.W))
}

@instantiable
class LanePopCount(val parameter: LanePopCountParam)
    extends FixedIOExtModule(new LanePopCountInterface(parameter))
    with SerializableModule[LanePopCountParam] {
  val paramDir = java.nio.file.Paths.get("zaozi-params")
  java.nio.file.Files.createDirectories(paramDir)
  java.nio.file.Files.write(
    paramDir.resolve("LanePopCount.json"),
    upickle.default.write(parameter).getBytes(java.nio.charset.StandardCharsets.UTF_8)
  )
}
