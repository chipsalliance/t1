// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_bin2gray

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_bin2gray._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_bin2gray(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p => SeqMap("width" -> IntParam(p.width))
    )
