import $ivy.`com.lihaoyi::mill-contrib-versionfile:`
import $ivy.`com.github.lolgab::mill-mima::0.0.23`

import mill._
import mill.scalalib._
import mill.define.{TaskModule, Command}
import mill.scalalib.publish._
import mill.scalalib.scalafmt._
import $file.common
import mill.contrib.versionfile.VersionFileModule
import com.github.lolgab.mill.mima._

object v {
  val scala = "2.13.12"
  val chiselPlugin = ivy"org.chipsalliance:::chisel-plugin:7.0.0-M1"
  val chisel = ivy"org.chipsalliance::chisel:7.0.0-M1"
  val mainargs = ivy"com.lihaoyi::mainargs:0.7.0"
}

trait ChiselInterfacePublishModule
    extends PublishModule
    with VersionFileModule
    with Mima {
  // Different artifacts has their own version for fine control of compatibility.
  def publishVersion: mill.T[String] = currentVersion().toString
  def pomSettings = T(
    PomSettings(
      description = artifactName(),
      organization = "org.chipsalliance",
      url = "https://github.com/chipsalliance/chisel-interface",
      licenses = Seq(License.`Apache-2.0`),
      versionControl =
        VersionControl.github("chipsalliance", "chisel-interface"),
      developers = Seq(
        Developer("sequencer", "Jiuyang Liu", "https://github.com/sequencer")
      )
    )
  )
}

object dwbb
    extends common.DWBBModule
    with ScalafmtModule
    with ChiselInterfacePublishModule {
  m =>
  def millSourcePath = os.pwd / "dwbb"
  def scalaVersion = T(v.scala)
  def chiselIvy = Some(v.chisel)
  def chiselPluginIvy = Some(v.chiselPlugin)
  def mainargsIvy = v.mainargs
}

object axi4
    extends common.AXI4Module
    with ScalafmtModule
    with ChiselInterfacePublishModule {
  m =>
  def millSourcePath = os.pwd / "axi4"
  def scalaVersion = T(v.scala)
  def chiselIvy = Some(v.chisel)
  def chiselPluginIvy = Some(v.chiselPlugin)
  def mainargsIvy = v.mainargs
}

object jtag
  extends common.JTAGModule
    with ScalafmtModule
    with ChiselInterfacePublishModule {
  m =>
  def millSourcePath = os.pwd / "jtag"
  def scalaVersion = T(v.scala)
  def chiselIvy = Some(v.chisel)
  def chiselPluginIvy = Some(v.chiselPlugin)
  def mainargsIvy = v.mainargs
}
