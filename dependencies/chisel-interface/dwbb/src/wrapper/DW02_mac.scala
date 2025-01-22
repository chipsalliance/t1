// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW02_mac

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW02_mac._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW02_mac(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap("A_width" -> IntParam(p.AWidth), "B_width" -> IntParam(p.BWidth))
    )
