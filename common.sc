// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

import mill._
import mill.scalalib._

trait HasChisel
  extends ScalaModule {
  // Define these for building chisel from source
  def chiselModule: Option[ScalaModule]

  override def moduleDeps = super.moduleDeps ++ chiselModule

  def chiselPluginJar: T[Option[PathRef]]

  override def scalacOptions = T(super.scalacOptions() ++ chiselPluginJar().map(path => s"-Xplugin:${path.path}"))

  override def scalacPluginClasspath: T[Agg[PathRef]] = T(super.scalacPluginClasspath() ++ chiselPluginJar())

  // Define these for building chisel from ivy
  def chiselIvy: Option[Dep]

  override def ivyDeps = T(super.ivyDeps() ++ chiselIvy)

  def chiselPluginIvy: Option[Dep]

  override def scalacPluginIvyDeps: T[Agg[Dep]] = T(super.scalacPluginIvyDeps() ++ chiselPluginIvy.map(Agg(_)).getOrElse(Agg.empty[Dep]))
}

// Local definitions
trait T1Module
  extends ScalaModule 
    with HasChisel {
  def arithmeticModule: ScalaModule
  def hardfloatModule: ScalaModule
  def tilelinkModule: ScalaModule
  def moduleDeps = super.moduleDeps ++ Seq(arithmeticModule, hardfloatModule, tilelinkModule)
}

trait ConfigGenModule
  extends ScalaModule {
  def t1Module: ScalaModule
  def moduleDeps = super.moduleDeps ++ Seq(t1Module)
  def mainargsIvy: Dep
  override def ivyDeps = T(super.ivyDeps() ++ Seq(mainargsIvy))
}

// T1 forked version of RocketCore
trait RocketModule
  extends ScalaModule
    with HasChisel {
  def rocketchipModule: ScalaModule
  def rvdecoderdbModule: ScalaModule
  def moduleDeps = super.moduleDeps ++ Seq(rocketchipModule, rvdecoderdbModule)
}

trait EmuHelperModule
  extends ScalaModule
    with HasChisel

trait IPEmulatorModule
  extends ScalaModule
    with HasChisel {
  def t1Module: ScalaModule
  def emuHelperModule: ScalaModule
  def moduleDeps = super.moduleDeps ++ Seq(t1Module, emuHelperModule)
}

trait SubsystemModule
  extends ScalaModule
    with HasChisel {
  def t1Module: ScalaModule
  def rocketModule: ScalaModule
  def moduleDeps = super.moduleDeps ++ Seq(t1Module, rocketModule)
}

trait SubsystemEmulatorModule
  extends ScalaModule
    with HasChisel {
  def subsystemModule: ScalaModule
  def emuHelperModule: ScalaModule
  def moduleDeps = super.moduleDeps ++ Seq(subsystemModule, emuHelperModule)
}

trait FPGAModule
  extends ScalaModule
    with HasChisel {
  def subsystemModule: ScalaModule
  def moduleDeps = super.moduleDeps ++ Seq(subsystemModule)
}

trait ElaboratorModule
  extends ScalaModule
    with HasChisel {
  def generators: Seq[ScalaModule]
  def circtPanamaBindingModule: ScalaModule
  def circtInstallPath: T[PathRef]
  override def moduleDeps = super.moduleDeps ++ Seq(circtPanamaBindingModule) ++ generators
  def mainargsIvy: Dep
  override def ivyDeps = T(super.ivyDeps() ++ Seq(mainargsIvy))

  override def javacOptions = T(super.javacOptions() ++ Seq("--enable-preview", "--release", "20"))

  override def forkArgs: T[Seq[String]] = T(
    super.forkArgs() ++ Seq("--enable-native-access=ALL-UNNAMED", "--enable-preview", s"-Djava.library.path=${ circtInstallPath().path / "lib"}")
  )
}
