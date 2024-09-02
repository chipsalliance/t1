// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder

import chisel3.experimental.hierarchy.Instantiate
import chisel3.properties.{ClassType, Property}

package object attribute {

  /** Attribute that will be encode the property of an instruction in the uarch and will be additional encode into the
    * object module, which will be used to provide metadata for verifications.
    */
  trait DecodeAttribute[T] {
    val identifier: String = this.getClass.getSimpleName.replace("$", "")
    val value:       T
    val description: String
    // Property of this attribute
    def om: Property[ClassType] = {
      val obj = Instantiate(new T1DecodeAttributeOM)
      obj.identifierIn  := Property(identifier)
      obj.descriptionIn := Property(description)
      // Use toString to avoid type issues...
      obj.valueIn       := Property(value.toString)
      obj.getPropertyReference
    }
  }

  sealed trait TriState
  case object Y  extends TriState
  case object N  extends TriState
  case object DC extends TriState

  trait Uop
  object UopDC                       extends Uop
  trait UopDecodeAttribute[T <: Uop] extends DecodeAttribute[T]

  trait BooleanDecodeAttribute extends DecodeAttribute[TriState]
  // TODO: we can add more scala type to avoid string type.
  trait StringDecodeAttribute  extends DecodeAttribute[String]
}
