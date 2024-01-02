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
trait VectorModule
  extends ScalaModule 
    with HasChisel {
  def arithmeticModule: ScalaModule
  def hardfloatModule: ScalaModule
  def tilelinkModule: ScalaModule
  def moduleDeps = super.moduleDeps ++ Seq(arithmeticModule, hardfloatModule, tilelinkModule)
}

// T1 forked version of RocketCore
trait RocketModule
  extends ScalaModule
    with HasChisel {
  def rocketchipModule: ScalaModule
  def rvdecoderdbModule: ScalaModule
  def moduleDeps = super.moduleDeps ++ Seq(rocketchipModule, rvdecoderdbModule)
}

trait IPEmulatorModule
  extends ScalaModule
    with HasChisel {
  def vectorModule: ScalaModule
  def moduleDeps = super.moduleDeps ++ Seq(vectorModule)
}

trait SubsystemEmulatorModule
  extends ScalaModule
    with HasChisel {
  def vectorModule: ScalaModule
  def rocketModule: ScalaModule
  def moduleDeps = super.moduleDeps ++ Seq(vectorModule, rocketModule)
}

trait ElaboratorModule
  extends ScalaModule
    with HasChisel {
  def generators: Seq[ScalaModule]
  override def moduleDeps = super.moduleDeps ++ generators
  def mainargsIvy: Dep
  override def ivyDeps = T(super.ivyDeps() ++ Seq(mainargsIvy))
}