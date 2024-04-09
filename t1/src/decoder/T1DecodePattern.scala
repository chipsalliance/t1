// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.hierarchy.core.Definition
import chisel3.experimental.hierarchy.{instantiable, public, Instantiate}
import chisel3.properties.{Class, ClassType, Property}
import chisel3.util.BitPat
import chisel3.util.experimental.decode.DecodePattern
import org.chipsalliance.rvdecoderdb.Instruction

@instantiable
class T1DecodeAttributeOM extends Class {
  val identifier = IO(Output(Property[String]()))
  val description = IO(Output(Property[String]()))
  val value = IO(Output(Property[String]()))
  @public val identifierIn = IO(Input(Property[String]()))
  @public val descriptionIn = IO(Input(Property[String]()))
  @public val valueIn = IO(Input(Property[String]()))
  identifier := identifierIn
  description := descriptionIn
  value := valueIn
}

@instantiable
class T1InstructionOM extends Class {
  // get type of [[T1DecodeAttributeOM]]
  val attributeClass = Instantiate.definition(new T1DecodeAttributeOM)
  val attributeClassTpe = attributeClass.getClassType

  val instructionName = IO(Output(Property[String]()))
  val documentation = IO(Output(Property[String]()))
  val bitPat = IO(Output(Property[String]()))
  val attributes = IO(Output(Property[Seq[attributeClassTpe.Type]]))

  @public val instructionNameIn = IO(Input(Property[String]()))
  @public val documentationIn = IO(Input(Property[String]()))
  @public val bitPatIn = IO(Input(Property[String]()))
  @public val attributesIn = IO(Input(Property[Seq[attributeClassTpe.Type]]))

  instructionName := instructionNameIn
  documentation := documentationIn
  bitPat := bitPatIn
  attributes := attributesIn
}

/** Attribute that will be encode into object module.
  * the Attribute is used to provide metadata for verifications.
  */
trait DecodeAttribute[T] {
  val identifier: String = this.getClass.getSimpleName.replace("$", "")
  val value:       T
  val description: String
  // Property of this attribute
  def om: Property[ClassType] = {
    val obj = Instantiate(new T1DecodeAttributeOM)
    obj.identifierIn := Property(identifier)
    obj.descriptionIn := Property(description)
    // Use toString to avoid type issues...
    obj.valueIn := Property(value.toString)
    obj.getPropertyReference
  }
}

trait BooleanDecodeAttribute extends DecodeAttribute[Boolean]
trait StringDecodeAttribute extends DecodeAttribute[String]

// All Attributes expose to OM,
case class IsVectorOM(value: Boolean) extends BooleanDecodeAttribute {
  override val description: String = "This instruction should be decode by T1."
}

case class UseLaneExecOM(value: String) extends StringDecodeAttribute {
  require(Seq("logic", "adder", "shift", "multiplier", "divider").contains(value), s"invalid execution type: ${value}")
  override val description: String =
    "Types of Execution Unit used in T1, can be logic, adder, shift, multiplier, divider"
}

/** A case class that should wrap all Vector Instructions.
  * This is used to store the attribute for Vector Instruction under the T1 uArch.
  * It generates [[chisel3.util.experimental.decode.TruthTable]], as well as documentation field.
  */
case class T1DecodePattern(instruction: Instruction, t1Parameter: T1Parameter) extends DecodePattern {
  override def bitPat: BitPat = BitPat("b" + instruction.encoding.toString)

  private def documentation: String = InstructionDocumentation(instruction, t1Parameter).toString

  // Below is the Scala in-memory attributes queried from DecodeTable.
  def isVector = instruction.instructionSet.name == "rv_v"

  /** goes into the [[org.chipsalliance.t1.rtl.decoder.TableGenerator.LaneDecodeTable.LogicUnit]]. */
  def isLogic = Seq(
    "vand.vi",
    "vand.vv",
    "vand.vx",
    "vmand.mm",
    "vmandn.mm",
    "vmnand.mm",
    "vredand.vs",
    "vmnor.mm",
    "vmor.mm",
    "vmorn.mm",
    "vmxnor.mm",
    "vmxor.mm",
    "vor.vi",
    "vor.vv",
    "vor.vx",
    "vredor.vs",
    "vredxor.vs",
    "vxor.vi",
    "vxor.vv",
    "vxor.vx"
  ).contains(instruction.name)

  def isAdder = Seq(
    "vaadd.vv",
    "vaadd.vx",
    "vaaddu.vv",
    "vaaddu.vx",
    "vadd.vi",
    "vadd.vv",
    "vadd.vx",
    "vmadd.vv",
    "vmadd.vx",
    "vsadd.vi",
    "vsadd.vv",
    "vsadd.vx",
    "vsaddu.vi",
    "vsaddu.vv",
    "vsaddu.vx",
    "vwadd.vv",
    "vwadd.vx",
    "vwadd.wv",
    "vwadd.wx",
    "vwaddu.vv",
    "vwaddu.vx",
    "vwaddu.wv",
    "vwaddu.wx",
    "vasub.vv",
    "vasub.vx",
    "vasubu.vv",
    "vasubu.vx",
    "vfmsub.vf",
    "vfmsub.vv",
    "vfnmsub.vf",
    "vfnmsub.vv",
    "vfrsub.vf",
    "vfsub.vf",
    "vfsub.vv",
    "vfwsub.vf",
    "vfwsub.vv",
    "vfwsub.wf",
    "vfwsub.wv",
    "vnmsub.vv",
    "vnmsub.vx",
    "vrsub.vi",
    "vrsub.vx",
    "vssub.vv",
    "vssub.vx",
    "vssubu.vv",
    "vssubu.vx",
    "vsub.vv",
    "vsub.vx",
    "vwsub.vv",
    "vwsub.vx",
    "vwsub.wv",
    "vwsub.wx",
    "vwsubu.vv",
    "vwsubu.vx",
    "vwsubu.wv",
    "vwsubu.wx",
    "vmslt.vv",
    "vmslt.vx",
    "vmsltu.vv",
    "vmsltu.vx"
  ).contains(instruction.name)

  // This is the OM for this instruction
  def om: Property[ClassType] = {
    val obj = Instantiate(new T1InstructionOM)
    // get type of [[T1DecodeAttributeOM]]
    val attributeClass = Definition(new T1DecodeAttributeOM)
    val attributeClassTpe = attributeClass.getClassType

    obj.instructionNameIn := Property(instruction.name)
    obj.bitPatIn := Property(bitPat.rawString)
    obj.documentationIn := Property(documentation)
    // convert in-memory attributes to Chisel Property
    obj.attributesIn :#= Property(
      Seq(
        IsVectorOM(isVector),
      ).map(_.om.as(attributeClassTpe))
    )
    obj.getPropertyReference
  }
}
