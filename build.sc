// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

import mill._
import mill.scalalib._
import mill.define.{Command, TaskModule}
import mill.scalalib.publish._
import mill.scalalib.scalafmt._
import mill.scalalib.TestModule.Utest
import mill.util.Jvm
import coursier.maven.MavenRepository
import $file.dependencies.chisel.build
import $file.dependencies.arithmetic.common
import $file.dependencies.`chisel-interface`.common
import $file.dependencies.`berkeley-hardfloat`.common
import $file.dependencies.rvdecoderdb.common
import $file.common

object v {
  val scala    = "2.13.14"
  val mainargs = ivy"com.lihaoyi::mainargs:0.5.0"
  val oslib    = ivy"com.lihaoyi::os-lib:0.9.1"
  val upickle  = ivy"com.lihaoyi::upickle:3.3.1"
  val spire    = ivy"org.typelevel::spire:latest.integration"
  val evilplot = ivy"io.github.cibotech::evilplot:latest.integration"
}

object chisel extends Chisel

trait Chisel extends millbuild.dependencies.chisel.build.Chisel {
  def crossValue              = v.scala
  override def millSourcePath = os.pwd / "dependencies" / "chisel"
}

object arithmetic extends Arithmetic

trait Arithmetic extends millbuild.dependencies.arithmetic.common.ArithmeticModule {
  override def millSourcePath = os.pwd / "dependencies" / "arithmetic" / "arithmetic"
  def scalaVersion            = T(v.scala)

  def chiselModule    = Some(chisel)
  def chiselPluginJar = T(Some(chisel.pluginModule.jar()))
  def chiselIvy       = None
  def chiselPluginIvy = None

  def spireIvy:    T[Dep] = v.spire
  def evilplotIvy: T[Dep] = v.evilplot
}

object axi4 extends AXI4

trait AXI4 extends millbuild.dependencies.`chisel-interface`.common.AXI4Module {
  override def millSourcePath = os.pwd / "dependencies" / "chisel-interface" / "axi4"
  def scalaVersion            = v.scala

  def mainargsIvy = v.mainargs

  def chiselModule    = Some(chisel)
  def chiselPluginJar = T(Some(chisel.pluginModule.jar()))
  def chiselIvy       = None
  def chiselPluginIvy = None
}

object hardfloat extends Hardfloat

trait Hardfloat extends millbuild.dependencies.`berkeley-hardfloat`.common.HardfloatModule {
  override def millSourcePath = os.pwd / "dependencies" / "berkeley-hardfloat" / "hardfloat"
  def scalaVersion            = T(v.scala)

  def chiselModule    = Some(chisel)
  def chiselPluginJar = T(Some(chisel.pluginModule.jar()))
  def chiselIvy       = None
  def chiselPluginIvy = None
}

object rvdecoderdb extends RVDecoderDB

trait RVDecoderDB extends millbuild.dependencies.rvdecoderdb.common.RVDecoderDBJVMModule with ScalaModule {
  def scalaVersion            = T(v.scala)
  def osLibIvy                = v.oslib
  def upickleIvy              = v.upickle
  override def millSourcePath = os.pwd / "dependencies" / "rvdecoderdb" / "rvdecoderdb"
}

object t1 extends T1

trait T1 extends millbuild.common.T1Module with ScalafmtModule {
  def scalaVersion = T(v.scala)

  def arithmeticModule  = arithmetic
  def axi4Module        = axi4
  def hardfloatModule   = hardfloat
  def rvdecoderdbModule = rvdecoderdb
  def riscvOpcodesPath  = T.input(PathRef(os.pwd / "dependencies" / "riscv-opcodes"))

  def chiselModule    = Some(chisel)
  def chiselPluginJar = T(Some(chisel.pluginModule.jar()))
  def chiselIvy       = None
  def chiselPluginIvy = None
}

object configgen extends ConfigGen

trait ConfigGen extends millbuild.common.ConfigGenModule with ScalafmtModule {
  def scalaVersion = T(v.scala)

  def t1Module = t1

  def mainargsIvy = v.mainargs
}

object rocketv extends RocketV

trait RocketV extends millbuild.common.RocketVModule with ScalafmtModule {
  def scalaVersion      = T(v.scala)
  def rvdecoderdbModule = rvdecoderdb
  def riscvOpcodesPath  = T.input(PathRef(os.pwd / "dependencies" / "riscv-opcodes"))
  def hardfloatModule   = hardfloat
  def axi4Module        = axi4

  def chiselModule    = Some(chisel)
  def chiselPluginJar = T(Some(chisel.pluginModule.jar()))
  def chiselPluginIvy = None
  def chiselIvy       = None
}

object t1rocket extends T1Rocket

trait T1Rocket extends millbuild.common.T1RocketModule with ScalafmtModule {
  def scalaVersion = T(v.scala)
  def rocketModule = rocketv
  def t1Module     = t1

  def chiselModule    = Some(chisel)
  def chiselPluginJar = T(Some(chisel.pluginModule.jar()))
  def chiselPluginIvy = None
  def chiselIvy       = None
}

object t1emu extends T1Emulator

trait T1Emulator extends millbuild.common.T1EmulatorModule {
  def scalaVersion = T(v.scala)

  def t1Module = t1

  def chiselModule    = Some(chisel)
  def chiselPluginJar = T(Some(chisel.pluginModule.jar()))
  def chiselPluginIvy = None
  def chiselIvy       = None
}

object rocketemu     extends RocketEmulator
trait RocketEmulator extends millbuild.common.RocketEmulatorModule {
  def scalaVersion = T(v.scala)

  def rocketVModule = rocketv

  def chiselModule    = Some(chisel)
  def chiselPluginJar = T(Some(chisel.pluginModule.jar()))
  def chiselPluginIvy = None
  def chiselIvy       = None
}

object t1rocketemu extends T1RocketEmulator

trait T1RocketEmulator extends millbuild.common.T1RocketEmulatorModule {
  def scalaVersion = T(v.scala)

  def t1rocketModule = t1rocket

  def chiselModule    = Some(chisel)
  def chiselPluginJar = T(Some(chisel.pluginModule.jar()))
  def chiselPluginIvy = None
  def chiselIvy       = None
}

object panamaconverter extends PanamaConverter

trait PanamaConverter extends millbuild.dependencies.chisel.build.PanamaConverter {
  def crossValue = v.scala

  override def millSourcePath = os.pwd / "dependencies" / "chisel" / "panamaconverter"

  def scalaVersion = T(v.scala)
}

// Module to generate RTL from json config
object elaborator extends Elaborator

trait Elaborator extends millbuild.common.ElaboratorModule {
  def scalaVersion = T(v.scala)

  def panamaconverterModule = panamaconverter

  def circtInstallPath = T.input(PathRef(os.Path(T.ctx().env("CIRCT_INSTALL_PATH"))))

  def generators = Seq(
    t1,
    t1emu,
    rocketv,
    rocketemu,
    t1rocket,
    t1rocketemu
  )

  def mainargsIvy = v.mainargs

  def chiselModule    = Some(chisel)
  def chiselPluginJar = T(Some(chisel.pluginModule.jar()))
  def chiselPluginIvy = None
  def chiselIvy       = None
}

object omreaderlib extends OMReaderLib

trait OMReaderLib extends millbuild.common.OMReaderLibModule {
  def scalaVersion = T(v.scala)

  def panamaconverterModule = panamaconverter

  def circtInstallPath = T.input(PathRef(os.Path(T.ctx().env("CIRCT_INSTALL_PATH"))))

  def mainargsIvy = v.mainargs

  def chiselModule    = Some(chisel)
  def chiselPluginJar = T(Some(chisel.pluginModule.jar()))
  def chiselPluginIvy = None
  def chiselIvy       = None
}

object omreader extends OMReader

trait OMReader extends millbuild.common.OMReaderModule {
  def scalaVersion = T(v.scala)

  def panamaconverterModule = panamaconverter
  def omreaderlibModule     = omreaderlib

  def circtInstallPath = T.input(PathRef(os.Path(T.ctx().env("CIRCT_INSTALL_PATH"))))

  def mainargsIvy = v.mainargs

  def chiselModule    = Some(chisel)
  def chiselPluginJar = T(Some(chisel.pluginModule.jar()))
  def chiselPluginIvy = None
  def chiselIvy       = None
}

/** A simple release flow for T1 generator: package required dependency to flat jar. usage: mill
  * t1package.{sourceJar,jar} out/t1package/sourceJar.dest/out.jar -> t1package-sources.jar
  * out/t1package/jar.dest/out.jar -> t1package.jar out/t1package/chiselPluginJar.dest/out.jar -> chiselPlugin.jar these
  * two jar is enough for this usages: object somepackagethatdependsont1 extends ScalaModule { def unmanagedClasspath =
  * T(Seq(PathRef(os.pwd / "t1package.jar"), PathRef(os.pwd / "t1package-sources.jar"))) } For Jiuyang's Team, this is
  * used for link T1 to NDA Blackboxes that cannot be open-sourced
  */
object t1package extends ScalaModule {
  def scalaVersion = T(v.scala)
  def moduleDeps   = super.moduleDeps ++ Seq(t1, t1emu, panamaconverter, omreaderlib)
  override def sourceJar: T[PathRef] = T(
    Jvm.createJar(
      T.traverse(transitiveModuleDeps)(dep => T.sequence(Seq(dep.allSources, dep.resources, dep.compileResources)))()
        .flatten
        .flatten
        .map(_.path)
        .filter(os.exists),
      manifest()
    )
  )
  def chiselPluginJar = T {
    val jar = T.dest / "out.jar"
    os.copy(chisel.pluginModule.jar().path, jar)
    PathRef(jar)
  }
}
