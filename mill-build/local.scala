package millbuild

import mill._

trait T1Deps extends Module {
  def chisel = Task { millbuild.submodules.rvdecoderdb.jvm.scalaVersion }
}
