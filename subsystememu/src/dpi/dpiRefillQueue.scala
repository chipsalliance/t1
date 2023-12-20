package org.chipsalliance.t1.subsystememu.dpi

import chisel3._

class dpiRefillQueue(triggerDelay: Int) extends DPIModule {
  val isImport: Boolean = true
  val clock = dpiTrigger("clock", Input(Bool()))

  override val trigger = s"always @(negedge ${clock.name}) #(${triggerDelay})"
}