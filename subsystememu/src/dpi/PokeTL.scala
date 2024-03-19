// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.subsystememu.dpi

import chisel3._
import freechips.rocketchip.tilelink.TLBundle

class PokeTL(bundle: TLBundle, triggerDelay: Int, desiredNameDPI: String = "PokeTL") extends DPIModuleLegacy {
  val isImport: Boolean = true
  val clock = dpiTrigger("clock", Input(Bool()))

  val channel = dpiIn("channel", Input(UInt(32.W)))

  val dBits = bundle.d.bits
  val dBits_opcode = dpiOut("dBits_opcode", Output(dBits.opcode))
  val dBits_param = dpiOut("dBits_param", Output(dBits.param))
  val dBits_size = dpiOut("dBits_size", Output(dBits.size))
  val dBits_source = dpiOut("dBits_source", Output(dBits.source))
  val dBits_sink = dpiOut("dBits_sink", Output(dBits.sink))
  val dBits_denied = dpiOut("dBits_denied", Output(dBits.denied))
  val dBits_data = dpiOut("dBits_data", Output(dBits.data))
  val dBits_corrupt = dpiOut("dBits_corrupt", Output(dBits.corrupt))

  val dValid = dpiOut("dValid", Output(bundle.d.valid))
  val aReady = dpiOut("aReady", Output(bundle.a.ready))

  override val trigger = s"always @(posedge ${clock.name}) #(${triggerDelay})"
  override def desiredName: String = desiredNameDPI
}
