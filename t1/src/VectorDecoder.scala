// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util.experimental.decode._
import org.chipsalliance.t1.rtl.decoder.Decoder

@instantiable
class VectorDecoder(fpuEnable: Boolean) extends Module {
  @public
  val decodeInput: UInt = IO(Input(UInt(32.W)))
  @public
  val decodeResult: DecodeBundle = IO(Output(new DecodeBundle(Decoder.all(fpuEnable))))

  decodeResult := Decoder.decode(fpuEnable)(decodeInput)
}
