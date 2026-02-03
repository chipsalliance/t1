// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_dct_2d

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import chisel3.util.log2Ceil
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    n: Int = 8,
    bpp: Int = 8,
    regOut: Boolean = false,
    tcMode: Boolean = false,
    rtMode: Boolean = true,
    idctMode: Boolean = false,
    coA: Int = 23170,
    coB: Int = 32138,
    coC: Int = 30274,
    coD: Int = 27245,
    coE: Int = 18205,
    coF: Int = 12541,
    coG: Int = 6393,
    coH: Int = 35355,
    coI: Int = 49039,
    coJ: Int = 46194,
    coK: Int = 41573,
    coL: Int = 27779,
    coM: Int = 19134,
    coN: Int = 9755,
    coO: Int = 35355,
    coP: Int = 49039
) extends SerializableModuleParameter {
  require(
    Range.inclusive(4, 16).contains(n),
    "n must be between 4 and 16"
  )
  require(
    Range.inclusive(4, 32).contains(bpp),
    "bpp must be between 4 and 32"
  )
  Seq(
    coA,
    coB,
    coC,
    coD,
    coE,
    coF,
    coG,
    coH,
    coI,
    coJ,
    coK,
    coL,
    coM,
    coN,
    coO,
    coP
  ).foreach { coef =>
    require(
      coef >= 0 && coef <= math.pow(2, 16).toInt,
      "coefficient must be between 0 and 2^16"
    )
  }
  val fnldat: Int = if (idctMode) bpp else (n / 2) + bpp
  val rddatsz: Int = if (idctMode) (n / 2) + bpp else bpp
  val idatsz: Int = (bpp / 2) + bpp + 4 + (if (!tcMode && !idctMode) 1 else 0)
  val addwidth: Int = log2Ceil(n * n)
}

class Interface(parameter: Parameter) extends Bundle {
  val clk: Clock = Input(Clock())
  val rst_n: Bool = Input(Bool())
  val init_n: Bool = Input(Bool())
  val enable: Bool = Input(Bool())
  val start: Bool = Input(Bool())
  val dct_rd_data: UInt = Input(UInt(parameter.rddatsz.W))
  val tp_rd_data: UInt = Input(UInt(parameter.idatsz.W))

  val done: Bool = Output(Bool())
  val ready: Bool = Output(Bool())
  val dct_rd_add: UInt = Output(UInt(parameter.addwidth.W))
  val tp_rd_add: UInt = Output(UInt(parameter.addwidth.W))
  val tp_wr_add: UInt = Output(UInt(parameter.addwidth.W))
  val tp_wr_n: Bool = Output(Bool())
  val tp_wr_data: UInt = Output(UInt(parameter.idatsz.W))
  val dct_wr_add: UInt = Output(UInt(parameter.addwidth.W))
  val dct_wr_n: Bool = Output(Bool())
  val dct_wr_data: UInt = Output(UInt(parameter.fnldat.W))
}
