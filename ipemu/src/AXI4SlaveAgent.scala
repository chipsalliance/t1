// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022-2024 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.ipemu.dpi

// TODO: upstream to AMBA as VIP
import chisel3._
import chisel3.choice.{Case, Group}
import org.chipsalliance.amba.axi4.bundle.AXI4BundleParameter

object AXI4BundleParameter {
  implicit def rw: upickle.default.ReadWriter[AXI4BundleParameter] =
    upickle.default.macroRW[AXI4BundleParameter]
}

case class AXI4SlaveAgentParameter(name: String, axiParameter: AXI4BundleParameter, outstanding: Int, readPayloadSize: Int, writePayloadSize: Int)

class AXI4SlaveAgentInterface(parameter: AXI4SlaveAgentParameter) extends Bundle {
  val clock:     Clock = Input(Clock())
  val reset:     Reset = Input(Reset())
  val channelId: UInt =  Input(Const(UInt(64.W)))
  // don't issue read DPI
  val gateRead: Bool = Input(Bool())
  // don't issue write DPI
  val gateWrite: Bool = Input(Bool())
  val channel = Flipped(
    org.chipsalliance.amba.axi4.bundle.verilog.irrevocable(parameter.axiParameter)
  )
}

abstract class AXI4SlaveAgent(parameter: AXI4SlaveAgentParameter)
  extends FixedIORawModule[AXI4SlaveAgentInterface](new AXI4SlaveAgentInterface(parameter))

object AXI4SlaveAgent extends Group {
  object EmptyAXI4SlaveAgent extends Case
  object PreciseAXI4SlaveAgent extends Case
  object SimpleAXI4SlaveAgent extends Case
}