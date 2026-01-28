// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.jtag.bundle

import chisel3.{Bool, Bundle}

/** Know as cJTAG. refer to
  * [[https://standards.ieee.org/ieee/1149.7/7703/]]
  * for more information
  */
class CompactJTAGVerilogBundle extends Bundle {
  val TMSC = Bool()
  val TCK = Bool()
}
