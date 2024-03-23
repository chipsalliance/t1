// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.ipemu.dpi

import chisel3._

import tilelink.TLBundle

class PeekTL(bundle: TLBundle, triggerDelay: Int) extends DPIModuleLegacy {
  val isImport: Boolean = true
  val clock = dpiTrigger("clock", Input(Bool()))
  val channel = dpiIn("channel", Input(UInt(32.W)))

  val aBits = bundle.a.bits
  val aBits_opcode = dpiIn("aBits_opcode", Input(aBits.opcode))
  val aBits_param = dpiIn("aBits_param", Input(aBits.param))
  val aBits_size = dpiIn("aBits_size", Input(aBits.size))
  val aBits_source = dpiIn("aBits_source", Input(aBits.source))
  val aBits_address = dpiIn("aBits_address", Input(aBits.address))
  val aBits_mask = dpiIn("aBits_mask", Input(aBits.mask))
  val aBits_data = dpiIn("aBits_data", Input(aBits.data))
  val aBits_corrupt = dpiIn("aBits_corrupt", Input(aBits.corrupt))
  val aValid = dpiIn("aValid", Input(bundle.a.valid))
  val dReady = dpiIn("dReady", Input(bundle.d.ready))

  override val trigger = s"always @(posedge ${clock.name}) #(${triggerDelay})"
}
