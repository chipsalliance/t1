// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022-2024 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.ipemu.dpi

import chisel3.dontTouch

/** Empty Module, it can be used in other flows */
class EmptyAXI4SlaveAgent(parameter: AXI4SlaveAgentParameter) extends AXI4SlaveAgent(parameter) {
  dontTouch(io)
}
