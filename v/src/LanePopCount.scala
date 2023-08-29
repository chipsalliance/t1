// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package v

import chisel3._
import chisel3.util.PopCount

class LanePopCount(datapathWidth: Int) extends Module {
  val src:  UInt = IO(Input(UInt(datapathWidth.W)))
  val resp: UInt = IO(Output(UInt(datapathWidth.W)))
  resp := PopCount(src)
}
