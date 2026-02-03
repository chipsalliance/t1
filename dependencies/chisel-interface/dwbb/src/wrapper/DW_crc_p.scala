// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_crc_p

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_crc_p._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_crc_p(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "data_width" -> IntParam(p.dataWidth),
          "poly_size" -> IntParam(p.polySize),
          "crc_cfg" -> IntParam(p.crcCfg),
          "bit_order" -> IntParam(p.bitOrder),
          "poly_coef0" -> IntParam(p.polyCoef0),
          "poly_coef1" -> IntParam(p.polyCoef1),
          "poly_coef2" -> IntParam(p.polyCoef2),
          "poly_coef3" -> IntParam(p.polyCoef3)
        )
    )
