// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.rtl.zvma

import me.jiuyang.zaozi.*
import me.jiuyang.zaozi.default.{*, given}
import me.jiuyang.zaozi.reftpe.*
import me.jiuyang.zaozi.valuetpe.*
import me.jiuyang.stdlib.*

case class SRAMParameter(depth: Int, width: Int) extends Parameter
given upickle.default.ReadWriter[SRAMParameter] = upickle.default.macroRW

class SRAMLayers(parameter: SRAMParameter) extends LayerInterface(parameter):
  def layers = Seq.empty

class SRAMIO(parameter: SRAMParameter) extends HWBundle(parameter):
  val clock:     BundleField[Clock] = Flipped(Clock())
  val enable:    BundleField[Bool]  = Flipped(Bool())
  val isWrite:   BundleField[Bool]  = Flipped(Bool())
  val address:   BundleField[UInt]  = Flipped(UInt(log2Ceil(parameter.depth)))
  val writeData: BundleField[Bits]  = Flipped(Bits(parameter.width))
  val readData:  BundleField[Bits]  = Aligned(Bits(parameter.width))

class SRAMProbe(parameter: SRAMParameter) extends DVBundle[SRAMParameter, SRAMLayers](parameter)

case class SRAMVerilogParams(depth: Int, width: Int, addrWidth: Int) extends VerilogParameter

@generator
object SRAM extends VerilogWrapper[SRAMParameter, SRAMLayers, SRAMIO, SRAMProbe, SRAMVerilogParams]:
  def verilogModuleName(parameter: SRAMParameter) = "SRAM1RW"
  def verilogParameter(parameter: SRAMParameter)  = SRAMVerilogParams(
    depth = parameter.depth,
    width = parameter.width,
    addrWidth = log2Ceil(parameter.depth)
  )
