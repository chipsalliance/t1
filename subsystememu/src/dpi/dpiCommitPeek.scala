package org.chipsalliance.t1.subsystememu.dpi

import chisel3._

class dpiCommitPeek(triggerDelay: Int) extends DPIModule {
  val isImport: Boolean = true

  val clock = dpiTrigger("clock", Input(Bool()))

  val llWen      = dpi("ll_wen"     , Input(Bool()))
  val rfWen      = dpi("rf_wen"     , Input(Bool()))
  val wbValid    = dpi("wb_valid"   , Input(Bool()))
  val rfWaddr    = dpi("rf_waddr"   , Input(UInt(32.W)))
  val rfWdata    = dpi("rf_wdata"   , Input(UInt(32.W)))
  val wbRegPC   = dpi("wb_reg_pc"   , Input(UInt(32.W)))
  val wbRegInst = dpi("wb_reg_inst" , Input(UInt(32.W)))

  override val trigger = s"always @(posedge ${clock.name}) #(${triggerDelay})"
}