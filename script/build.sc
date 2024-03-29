// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

import mill._
import mill.scalalib._
import mill.scalalib.publish._
import mill.scalalib.scalafmt._

object v {
  val scala3 = "3.3.3"
  val mainargs = ivy"com.lihaoyi::mainargs:0.5.0"
  val oslib = ivy"com.lihaoyi::os-lib:0.9.1"
}

object script extends ScalaModule {
  def scalaVersion = v.scala3
  override def ivyDeps = Agg(v.mainargs, v.oslib)
}
