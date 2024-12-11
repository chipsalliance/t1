// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder

import chisel3._
import chisel3.experimental.hierarchy.core.Definition
import chisel3.experimental.hierarchy.{instantiable, public, Instantiate}
import chisel3.properties.{AnyClassType, Class, ClassType, Property}
import chisel3.util.BitPat
import chisel3.util.experimental.decode.DecodePattern
import org.chipsalliance.rvdecoderdb.Instruction
import org.chipsalliance.t1.rtl.T1Parameter
import org.chipsalliance.t1.rtl.decoder.attribute._

@instantiable
class T1DecodeAttributeOM(
  _identifier:  String,
  _description: String,
  _value:       String)
    extends Class {
  val identifier  = IO(Output(Property[String]()))
  val description = IO(Output(Property[String]()))
  val value       = IO(Output(Property[String]()))
  identifier  := Property(_identifier)
  description := Property(_description)
  value       := Property(_value)
}

@instantiable
class T1InstructionOM(
  _instructionName: String,
  _documentation:   String,
  _bitPat:          String)
    extends Class {
  val instructionName = IO(Output(Property[String]()))
  val documentation   = IO(Output(Property[String]()))
  val bitPat          = IO(Output(Property[String]()))
  val attributes      = IO(Output(Property[Seq[AnyClassType]]))
  @public
  val attributesIn    = IO(Input(Property[Seq[AnyClassType]]))

  instructionName := Property(_instructionName)
  documentation   := Property(_documentation)
  bitPat          := Property(_bitPat)
  attributes      := attributesIn
}

/** A case class that should wrap all Vector Instructions. This is used to store the attribute for Vector Instruction
  * under the T1 uArch. It generates [[chisel3.util.experimental.decode.TruthTable]], as well as documentation field.
  */
case class T1DecodePattern(instruction: Instruction, param: DecoderParam) extends DecodePattern {
  override def bitPat: BitPat = BitPat("b" + instruction.encoding.toString)

  // use the attribute w/ [[isVector.value]]
  def isVector:                isVector                = attribute.isVector(this)
  def isAdder:                 isAdder                 = attribute.isAdder(this)
  def isAverage:               isAverage               = attribute.isAverage(this)
  def isCompress:              isCompress              = attribute.isCompress(this)
  def isCrossread:             isCrossread             = attribute.isCrossread(this)
  def isCrosswrite:            isCrosswrite            = attribute.isCrosswrite(this)
  def isDivider:               isDivider               = attribute.isDivider(this)
  def isDontneedexecuteinlane: isDontneedexecuteinlane = attribute.isDontneedexecuteinlane(this)
  def isExtend:                isExtend                = attribute.isExtend(this)
  def isFcompare:              isFcompare              = attribute.isFcompare(this)
  def isFfo:                   isFfo                   = attribute.isFfo(this)
  def isFirstwiden:            isFirstwiden            = attribute.isFirstwiden(this)
  def isFloatmul:              isFloatmul              = attribute.isFloatmul(this)
  def isFloat:                 isFloat                 = attribute.isFloat(this)
  def isFloattype:             isFloattype             = attribute.isFloattype(this)
  def isFma:                   isFma                   = attribute.isFma(this)
  def isFother:                isFother                = attribute.isFother(this)
  def isGather16:              isGather16              = attribute.isGather16(this)
  def isGather:                isGather                = attribute.isGather(this)
  def isId:                    isId                    = attribute.isId(this)
  def isIndextype:             isIndextype             = attribute.isIndextype(this)
  def isIota:                  isIota                  = attribute.isIota(this)
  def isItype:                 isItype                 = attribute.isItype(this)
  def isLogic:                 isLogic                 = attribute.isLogic(this)
  def isMaskdestination:       isMaskdestination       = attribute.isMaskdestination(this)
  def isMasklogic:             isMasklogic             = attribute.isMasklogic(this)
  def isMasksource:            isMasksource            = attribute.isMasksource(this)
  def isMaskunit:              isMaskunit              = attribute.isMaskunit(this)
  def isMulticycle:            isMulticycle            = attribute.isMulticycle(this)
  def isMultiplier:            isMultiplier            = attribute.isMultiplier(this)
  def isMv:                    isMv                    = attribute.isMv(this)
  def isNarrow:                isNarrow                = attribute.isNarrow(this)
  def isNr:                    isNr                    = attribute.isNr(this)
  def isOrderreduce:           isOrderreduce           = attribute.isOrderreduce(this)
  def isOther:                 isOther                 = attribute.isOther(this)
  def isZero:                  isZero                  = attribute.isZero(this)
  def isPopcount:              isPopcount              = attribute.isPopcount(this)
  def isReadonly:              isReadonly              = attribute.isReadonly(this)
  def isRed:                   isRed                   = attribute.isRed(this)
  def isReverse:               isReverse               = attribute.isReverse(this)
  def isSaturate:              isSaturate              = attribute.isSaturate(this)
  def isScheduler:             isScheduler             = attribute.isScheduler(this)
  def isShift:                 isShift                 = attribute.isShift(this)
  def isSlid:                  isSlid                  = attribute.isSlid(this)
  def isSpecial:               isSpecial               = attribute.isSpecial(this)
  def isSpecialslot:           isSpecialslot           = attribute.isSpecialslot(this)
  def isSreadvd:               isSreadvd               = attribute.isSreadvd(this)
  def isSwrite:                isSwrite                = attribute.isSwrite(this)
  def isTargetrd:              isTargetrd              = attribute.isTargetrd(this)
  def isUnorderwrite:          isUnorderwrite          = attribute.isUnorderwrite(this)
  def isUnsigned0:             isUnsigned0             = attribute.isUnsigned0(this)
  def isUnsigned1:             isUnsigned1             = attribute.isUnsigned1(this)
  def isVtype:                 isVtype                 = attribute.isVtype(this)
  def isVwmacc:                isVwmacc                = attribute.isVwmacc(this)
  def isWidenreduce:           isWidenreduce           = attribute.isWidenreduce(this)
  def isZvbb:                  isZvbb                  = attribute.isZvbb(this)
  def fpExecutionType:         FpExecutionType.Type    = attribute.FpExecutionType(this)
  def topUop:                  TopUop                  = attribute.TopUop(this)
  def decoderUop:              DecoderUop              = attribute.DecoderUop(this)

  private def documentation: String = InstructionDocumentation(instruction, param).toString

  // This is the OM for this instruction
  def om: Property[ClassType] = {
    val obj = Instantiate(
      new T1InstructionOM(
        instruction.name,
        bitPat.rawString,
        documentation
      )
    )
    // convert in-memory attributes to Chisel Property
    // get type of [[T1DecodeAttributeOM]]
    obj.attributesIn :#= Property(
      Seq(
        isVector,
        isAdder,
        isAverage,
        isCompress,
        isCrossread,
        isCrosswrite,
        isDivider,
        isDontneedexecuteinlane,
        isExtend,
        isFcompare,
        isFfo,
        isFirstwiden,
        isFloatmul,
        isFloat,
        isFloattype,
        isFma,
        isFother,
        isGather16,
        isGather,
        isId,
        isIndextype,
        isIota,
        isItype,
        isLogic,
        isMaskdestination,
        isMasklogic,
        isMasksource,
        isMaskunit,
        isMulticycle,
        isMultiplier,
        isMv,
        isNarrow,
        isNr,
        isOrderreduce,
        isOther,
        isPopcount,
        isReadonly,
        isRed,
        isReverse,
        isSaturate,
        isScheduler,
        isShift,
        isSlid,
        isSpecial,
        isSpecialslot,
        isSreadvd,
        isSwrite,
        isTargetrd,
        isUnorderwrite,
        isUnsigned0,
        isUnsigned1,
        isVtype,
        isVwmacc,
        isWidenreduce
      ).map(_.om.asAnyClassType)
    )
    obj.getPropertyReference
  }
}
