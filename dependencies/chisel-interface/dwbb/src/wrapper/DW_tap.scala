// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_tap

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_tap._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_tap(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "width" -> IntParam(p.width),
          "id" -> IntParam(if (p.id) 1 else 0),
          "version" -> IntParam(p.version),
          "part" -> IntParam(p.part),
          "man_num" -> IntParam(p.manNum),
          "sync_mode" -> IntParam(p.syncMode match {
            case "sync"  => 1
            case "async" => 0
          }),
          "tst_mode" -> IntParam(if (p.tstMode) 1 else 0)
        )
    )
