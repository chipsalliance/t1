// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DWF_dp_rnd_tc

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DWF_dp_rnd_tc._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DWF_dp_rnd_tc(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "width" -> IntParam(p.width),
          "lsb" -> IntParam(p.lsb)
        )
    )
