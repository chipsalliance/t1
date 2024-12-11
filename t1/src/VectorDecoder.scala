// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.SerializableModule
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import chisel3.properties.{AnyClassType, ClassType, Property}
import chisel3.util.experimental.decode._
import org.chipsalliance.stdlib.GeneralOM
import org.chipsalliance.t1.rtl.decoder.{Decoder, DecoderParam}

@instantiable
class VectorDecoderOM(parameter: DecoderParam) extends GeneralOM[DecoderParam, VectorDecoder](parameter) {
  val instructions = IO(Output(Property[Seq[AnyClassType]]()))
  instructions := Property(Decoder.allDecodePattern(parameter).map(_.om.asAnyClassType))
}

// TODO: FixedIOModule
@instantiable
class VectorDecoder(val parameter: DecoderParam) extends RawModule with SerializableModule[DecoderParam] {
  val omInstance: Instance[VectorDecoderOM] = Instantiate(new VectorDecoderOM(parameter))
  val omType:     ClassType                 = omInstance.toDefinition.getClassType
  @public
  val om:         Property[ClassType]       = IO(Output(Property[omType.Type]()))
  om := omInstance.getPropertyReference

  @public
  val decodeInput:  UInt         = IO(Input(UInt(32.W)))
  @public
  val decodeResult: DecodeBundle = IO(Output(new DecodeBundle(Decoder.allFields(parameter))))
  decodeResult := Decoder.decode(parameter)(decodeInput)
}
