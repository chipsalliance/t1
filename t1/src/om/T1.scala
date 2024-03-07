package org.chipsalliance.t1.om

import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.properties._
import chisel3.{Input, Output}
import org.chipsalliance.t1.rtl.T1Parameter

@instantiable
class T1(parameter: T1Parameter) extends Class {
  val retimes = IO(Output(Property[Seq[Path]]()))
  @public val retimesIn = IO(Input(Property[Seq[Path]]()))
  retimes := retimesIn
}
