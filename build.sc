// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

import mill._
import mill.scalalib._
import mill.define.{Command, TaskModule}
import mill.scalalib.publish._
import mill.scalalib.scalafmt._
import mill.scalalib.TestModule.Utest
import coursier.maven.MavenRepository
import $file.dependencies.chisel.build
import $file.dependencies.arithmetic.common
import $file.dependencies.tilelink.common
import $file.dependencies.`berkeley-hardfloat`.common
import $file.dependencies.rvdecoderdb.common
import $file.common

object v {
  val scala = "2.13.12"
  val mainargs = ivy"com.lihaoyi::mainargs:0.5.0"
  val json4sJackson = ivy"org.json4s::json4s-jackson:4.0.5"
  val oslib = ivy"com.lihaoyi::os-lib:0.9.1"
  val upickle = ivy"com.lihaoyi::upickle:3.1.3"
  val scalaReflect = ivy"org.scala-lang:scala-reflect:${scala}"
  val bc = ivy"org.bouncycastle:bcprov-jdk15to18:latest.integration"
  val spire = ivy"org.typelevel::spire:latest.integration"
  val evilplot = ivy"io.github.cibotech::evilplot:latest.integration"
}

object chisel extends Chisel

trait Chisel 
  extends millbuild.dependencies.chisel.build.Chisel {
  def crossValue = v.scala
  override def millSourcePath = os.pwd / "dependencies" / "chisel"
}

object arithmetic extends Arithmetic

trait Arithmetic 
  extends millbuild.dependencies.arithmetic.common.ArithmeticModule {
  override def millSourcePath = os.pwd / "dependencies" / "arithmetic" / "arithmetic"
  def scalaVersion = T(v.scala)

  def chiselModule = Some(chisel)
  def chiselPluginJar = T(Some(chisel.pluginModule.jar()))
  def chiselIvy = None
  def chiselPluginIvy = None

  def spireIvy: T[Dep] = v.spire
  def evilplotIvy: T[Dep] = v.evilplot
}

object tilelink extends TileLink

trait TileLink
  extends millbuild.dependencies.tilelink.common.TileLinkModule {
  override def millSourcePath = os.pwd / "dependencies" / "tilelink" / "tilelink"
  def scalaVersion = T(v.scala)
 
  def chiselModule = Some(chisel)
  def chiselPluginJar = T(Some(chisel.pluginModule.jar()))
  def chiselIvy = None
  def chiselPluginIvy = None
}

object hardfloat extends Hardfloat

trait Hardfloat
  extends millbuild.dependencies.`berkeley-hardfloat`.common.HardfloatModule {
  override def millSourcePath = os.pwd / "dependencies" / "berkeley-hardfloat" / "hardfloat"
  def scalaVersion = T(v.scala)

  def chiselModule = Some(chisel)
  def chiselPluginJar = T(Some(chisel.pluginModule.jar()))
  def chiselIvy = None
  def chiselPluginIvy = None
}

object rvdecoderdb extends RVDecoderDB

trait RVDecoderDB
  extends millbuild.dependencies.rvdecoderdb.common.RVDecoderDBJVMModule
    with ScalaModule {
  def scalaVersion = T(v.scala)
  def osLibIvy = v.oslib
  def upickleIvy = v.upickle
  override def millSourcePath = os.pwd / "dependencies" / "rvdecoderdb" / "rvdecoderdb"
}

object t1 extends T1

trait T1
  extends millbuild.common.T1Module
  with ScalafmtModule {
  def scalaVersion = T(v.scala)

  def arithmeticModule = arithmetic
  def tilelinkModule = tilelink
  def hardfloatModule = hardfloat
  def rvdecoderdbModule = rvdecoderdb
  def riscvOpcodesPath = T.input(PathRef(os.pwd / "dependencies" / "riscv-opcodes"))

  def chiselModule = Some(chisel)
  def chiselPluginJar = T(Some(chisel.pluginModule.jar()))
  def chiselIvy = None
  def chiselPluginIvy = None
}

object configgen extends ConfigGen

trait ConfigGen
  extends millbuild.common.ConfigGenModule
    with ScalafmtModule {
  def scalaVersion = T(v.scala)

  def t1Module = t1

  def mainargsIvy = v.mainargs
}

// SoC demostration, not the real dependencies for the vector project
import $file.dependencies.`cde`.common
import $file.dependencies.`rocket-chip`.common

object cde extends CDE

trait CDE
  extends millbuild.dependencies.cde.common.CDEModule {
  override def millSourcePath = os.pwd / "dependencies" / "cde" / "cde"
  def scalaVersion = T(v.scala)
}

object rocketchip extends RocketChip

trait RocketChip
  extends millbuild.dependencies.`rocket-chip`.common.RocketChipModule {
  override def millSourcePath = os.pwd / "dependencies" / "rocket-chip"
  def scalaVersion = T(v.scala)

  def chiselModule = Some(chisel)
  def chiselPluginJar = T(Some(chisel.pluginModule.jar()))
  def chiselIvy = None
  def chiselPluginIvy = None

  def macrosModule = macros
  def hardfloatModule = hardfloat
  def cdeModule = cde
  def mainargsIvy = v.mainargs
  def json4sJacksonIvy = v.json4sJackson
}

object macros extends Macros

trait Macros
  extends millbuild.dependencies.`rocket-chip`.common.MacrosModule {

  override def millSourcePath = os.pwd / "dependencies" / "rocket-chip" / "macros"
  def scalaVersion: T[String] = T(v.scala)

  def scalaReflectIvy = v.scalaReflect
}

// we maintain our own Rocket for T1

object rocket extends Rocket

trait Rocket
  extends millbuild.common.RocketModule
    with ScalafmtModule {
  def scalaVersion = T(v.scala)

  def rvdecoderdbModule = rvdecoderdb
  def rocketchipModule = rocketchip
  def riscvOpcodesPath = T.input(PathRef(os.pwd / "dependencies" / "riscv-opcodes"))

  def chiselModule = Some(chisel)
  def chiselPluginJar = T(Some(chisel.pluginModule.jar()))
  def chiselPluginIvy = None
  def chiselIvy = None
}

object emuhelper extends EmuHelper

trait EmuHelper
  extends millbuild.common.EmuHelperModule {
  def scalaVersion = T(v.scala)

  def chiselModule = Some(chisel)
  def chiselPluginJar = T(Some(chisel.pluginModule.jar()))
  def chiselPluginIvy = None
  def chiselIvy = None
}

object ipemu extends IPEmulator

trait IPEmulator
  extends millbuild.common.IPEmulatorModule {
  def scalaVersion = T(v.scala)

  def t1Module = t1
  def emuHelperModule = emuhelper

  def chiselModule = Some(chisel)
  def chiselPluginJar = T(Some(chisel.pluginModule.jar()))
  def chiselPluginIvy = None
  def chiselIvy = None
}

object subsystem extends Subsystem

trait Subsystem
  extends millbuild.common.SubsystemModule {
  def scalaVersion = T(v.scala)

  def t1Module = t1
  def rocketModule = rocket

  def chiselModule = Some(chisel)
  def chiselPluginJar = T(Some(chisel.pluginModule.jar()))
  def chiselPluginIvy = None
  def chiselIvy = None
}

object subsystememu extends SubsystemEmulator

trait SubsystemEmulator
  extends millbuild.common.SubsystemEmulatorModule {
  def scalaVersion = T(v.scala)

  def subsystemModule = subsystem
  def emuHelperModule = emuhelper

  def chiselModule = Some(chisel)
  def chiselPluginJar = T(Some(chisel.pluginModule.jar()))
  def chiselPluginIvy = None
  def chiselIvy = None
}

object fpga extends FPGA

trait FPGA
  extends millbuild.common.FPGAModule {
  def scalaVersion = T(v.scala)

  def subsystemModule = subsystem

  def chiselModule = Some(chisel)
  def chiselPluginJar = T(Some(chisel.pluginModule.jar()))
  def chiselPluginIvy = None
  def chiselIvy = None
}

// Module to generate RTL from json config
object elaborator extends Elaborator

trait Elaborator
  extends millbuild.common.ElaboratorModule {
  def scalaVersion = T(v.scala)

  def generators = Seq(
    t1,
    ipemu,
    subsystem,
    subsystememu,
    fpga
  )

  def mainargsIvy = v.mainargs

  def chiselModule = Some(chisel)
  def chiselPluginJar = T(Some(chisel.pluginModule.jar()))
  def chiselPluginIvy = None
  def chiselIvy = None
}
