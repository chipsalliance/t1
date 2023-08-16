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

// Local definations
trait VectorModule
  extends ScalaModule 
    with HasChisel {
  def arithmeticModule: ScalaModule
  def hardfloatModule: ScalaModule
  def tilelinkModule: ScalaModule
  def moduleDeps = super.moduleDeps ++ Seq(arithmeticModule, hardfloatModule, tilelinkModule)
}

trait VectorSubsystemModule
  extends ScalaModule
    with HasChisel {
  def vectorModule: ScalaModule
  def rocketchipModule: ScalaModule
  def inclusivecacheModule: ScalaModule
  def moduleDeps = super.moduleDeps ++ Seq(vectorModule, rocketchipModule, inclusivecacheModule)
}
