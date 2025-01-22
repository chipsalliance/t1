// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_crc_p

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    dataWidth: Int = 16,
    polySize: Int = 16,
    crcCfg: Int = 7,
    bitOrder: Int = 3,
    polyCoef0: Int = 4129,
    polyCoef1: Int = 0,
    polyCoef2: Int = 0,
    polyCoef3: Int = 0
) extends SerializableModuleParameter {
  require(
    Range.inclusive(1, 512).contains(dataWidth),
    "dataWidth must be between 1 and 512"
  )
  require(
    Range.inclusive(2, 64).contains(polySize),
    "polySize must be between 2 and 64"
  )
  require(
    Range.inclusive(0, 7).contains(crcCfg),
    "crcCfg must be between 0 and 7"
  )
  require(
    Range.inclusive(0, 3).contains(bitOrder),
    "bitOrder must be between 0 and 3"
  )
  require(
    Range.inclusive(1, 65535).contains(polyCoef0),
    "polyCoef0 must be between 1 and 65535"
  )
  require(
    Range.inclusive(0, 65535).contains(polyCoef1),
    "polyCoef1 must be between 0 and 65535"
  )
  require(
    Range.inclusive(0, 65535).contains(polyCoef2),
    "polyCoef2 must be between 0 and 65535"
  )
  require(
    Range.inclusive(0, 65535).contains(polyCoef3),
    "polyCoef3 must be between 0 and 65535"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val data_in: UInt = Input(UInt(parameter.dataWidth.W))
  val crc_in: UInt = Input(UInt(parameter.polySize.W))
  val crc_ok: Bool = Output(Bool())
  val crc_out: UInt = Output(UInt(parameter.polySize.W))
}
