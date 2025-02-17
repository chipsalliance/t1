package build

import mill._

object snapshots {
  val chisel   = ivy"org.chipsalliance::chisel:0.0.0+0-no-vcs-SNAPSHOT"
  val panamaconverter = ivy"org.chipsalliance::panamaconverter-cross:0.0.0+0-no-vcs-SNAPSHOT"
  val chiselPlugin = ivy"org.chipsalliance:::chisel-plugin:0.0.0+0-no-vcs-SNAPSHOT"
  val axi4 = ivy"org.chipsalliance::axi4-snapshot:0.0.0+0-no-vcs-SNAPSHOT"
  val dwbb = ivy"org.chipsalliance::dwbb-snapshot:0.0.0+0-no-vcs-SNAPSHOT"
  val jtag = ivy"org.chipsalliance::jtag-snapshot:0.0.0+0-no-vcs-SNAPSHOT"
  val arithmetic = ivy"me.jiuyang::arithmetic-snapshot:0.0.0+0-no-vcs-SNAPSHOT"
  val rvdecoderdb = ivy"me.jiuyang::rvdecoderdb-jvm:0.0.0+0-no-vcs-SNAPSHOT"
  val hardfloat = ivy"edu.berkeley.cs::hardfloat-snapshot:0.0.0-SNAPSHOT"
}
