// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import chisel3.properties.{AnyClassType, Class, ClassType, Property}
import chisel3.util.experimental.decode._
import org.chipsalliance.t1.rtl.decoder.{Decoder, DecoderParam}

@instantiable
class VectorDecoderOM extends Class {
  @public
  val instructions   = IO(Output(Property[Seq[AnyClassType]]()))
  @public
  val instructionsIn = IO(Input(Property[Seq[AnyClassType]]()))
  instructions := instructionsIn
}

@instantiable
class VectorDecoder(param: DecoderParam) extends Module {
  val omInstance: Instance[VectorDecoderOM] = Instantiate(new VectorDecoderOM)
  val omType:     ClassType                 = omInstance.toDefinition.getClassType
  @public
  val om:         Property[ClassType]       = IO(Output(Property[omType.Type]()))
  om := omInstance.getPropertyReference

  @public
  val decodeInput:  UInt         = IO(Input(UInt(32.W)))
  @public
  val decodeResult: DecodeBundle = IO(Output(new DecodeBundle(Decoder.allFields(param))))

  omInstance.instructionsIn := Property(Decoder.allDecodePattern(param).map(_.om.asAnyClassType))

  decodeResult := Decoder.decode(param)(decodeInput)
}
