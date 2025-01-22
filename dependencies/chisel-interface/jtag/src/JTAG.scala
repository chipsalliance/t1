// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.jtag.bundle

import chisel3.{Bool, Bundle, Flipped}

case class JTAGBundleParameter(hasTrst: Boolean)

/** Known as JTAG, refer to
  * [[https://standards.ieee.org/ieee/1149.1/4484/]]
  * [[https://www.jedec.org/standards-documents/docs/jesd-71]]
  * for more information
  */
class JTAGVerilogBundle(parameter: JTAGBundleParameter) extends Bundle {
  val TDI = Bool()
  val TDO = Flipped(Bool())
  val TCK = Bool()
  val TMS = Bool()
  val TRST = Option.when(parameter.hasTrst)(Bool())
}
