package millbuild

import $packages._
import mill._

trait T1Deps extends Module {
  def chisel = Task { millbuild.submodules.arithmetic.scala }
}
