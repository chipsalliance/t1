// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.montior

import chisel3._
import chisel3.util.experimental.BoringUtils.tapAndRead
import org.chipsalliance.t1.rtl.V

class Retire(dut: V) {
  val valid: Bool = Wire(Bool())
  valid := tapAndRead(dut.response.fire)
}
