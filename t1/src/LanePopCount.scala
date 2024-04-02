// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util.PopCount

@instantiable
class LanePopCount(datapathWidth: Int) extends Module {
  @public
  val src:  UInt = IO(Input(UInt(datapathWidth.W)))
  @public
  val resp: UInt = IO(Output(UInt(datapathWidth.W)))
  resp := PopCount(src)
}
