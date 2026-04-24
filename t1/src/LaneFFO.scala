// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.instantiable
import chisel3.util.{Valid, ValidIO}

object LaneFFOParam {
  implicit def rw: upickle.default.ReadWriter[LaneFFOParam] = upickle.default.macroRW
}

case class LaneFFOParam(datapathWidth: Int) extends SerializableModuleParameter

class LaneFFOInterface(parameter: LaneFFOParam) extends Bundle {
  val clock:        Clock         = Input(Clock())
  val reset:        Bool          = Input(Bool())
  val src:          Vec[UInt]     = Input(Vec(4, UInt(parameter.datapathWidth.W)))
  val resultSelect: UInt          = Input(UInt(2.W))
  val complete:     Bool          = Input(Bool())
  val maskType:     Bool          = Input(Bool())
  val resp:         ValidIO[UInt] = Output(Valid(UInt(parameter.datapathWidth.W)))
}

@instantiable
class LaneFFO(val parameter: LaneFFOParam)
    extends FixedIOExtModule(new LaneFFOInterface(parameter))
    with SerializableModule[LaneFFOParam] {
  val paramDir = java.nio.file.Paths.get("zaozi-params")
  java.nio.file.Files.createDirectories(paramDir)
  java.nio.file.Files.write(
    paramDir.resolve("LaneFFO.json"),
    upickle.default.write(parameter).getBytes(java.nio.charset.StandardCharsets.UTF_8)
  )
}
