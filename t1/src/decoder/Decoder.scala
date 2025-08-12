// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import chisel3.util.BitPat
import chisel3.util.experimental.decode._
import org.chipsalliance.rvdecoderdb.Instruction
import org.chipsalliance.t1.rtl.decoder.attribute._

object DecoderParam {
  implicit def rwP: upickle.default.ReadWriter[DecoderParam] = upickle.default.macroRW
}

case class DecoderParam(fpuEnable: Boolean, zvbbEnable: Boolean, useXsfmm: Boolean, allInstructions: Seq[Instruction])
    extends SerializableModuleParameter

trait T1DecodeFiled[D <: Data] extends DecodeField[T1DecodePattern, D] with FieldName

trait BoolField extends T1DecodeFiled[Bool] with BoolDecodeField[T1DecodePattern] {
  def getTriState(pattern: T1DecodePattern): TriState

  override def genTable(pattern: T1DecodePattern): BitPat =
    getTriState(pattern) match {
      case attribute.Y  => y
      case attribute.N  => n
      case attribute.DC => dc
    }
}

trait T1UopField extends T1DecodeFiled[UInt] with FieldName {
  def chiselType: UInt = UInt(4.W)
}

trait T1TopUopField extends T1DecodeFiled[UInt] with FieldName {
  def chiselType: UInt = UInt(5.W)
}

trait T1fpExecutionTypeUopField extends T1DecodeFiled[UInt] with FieldName {
  def chiselType: UInt = UInt(2.W)
}

object Decoder {
  object logic extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isLogic.value
  }

  object adder extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isAdder.value
  }

  object shift extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isShift.value
  }

  object multiplier extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isMultiplier.value
  }

  object divider extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isDivider.value
  }

  object multiCycle extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isMulticycle.value
  }

  object other extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isOther.value
  }

  object unsigned0 extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isUnsigned0.value
  }

  object unsigned1 extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isUnsigned1.value
  }

  object itype extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isItype.value
  }

  object nr extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isNr.value
  }

  object red extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isRed.value
  }

  object widenReduce extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isWidenreduce.value
  }

  object targetRd extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isTargetrd.value
  }

  object slid extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isSlid.value
  }

  object gather extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isGather.value
  }

  object gather16 extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isGather16.value
  }

  object compress extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isCompress.value
  }

  object unOrderWrite extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isUnorderwrite.value
  }

  object extend extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isExtend.value
  }

  object mv extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isMv.value
  }

  object iota extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isIota.value
  }

  object maskLogic extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isMasklogic.value
  }

  object maskDestination extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isMaskdestination.value
  }

  object maskSource extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isMasksource.value
  }

  object readOnly extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isReadonly.value
  }

  object vwmacc extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isVwmacc.value
  }

  object saturate extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isSaturate.value
  }

  object special extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isSpecial.value
  }

  object maskUnit extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isMaskunit.value
  }

  object crossWrite extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isCrosswrite.value
  }

  object crossRead extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isCrossread.value
  }

  object sWrite extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isSwrite.value
  }

  object vtype extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isVtype.value
  }

  object sReadVD extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isSreadvd.value
  }

  object scheduler extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isScheduler.value
  }

  object dontNeedExecuteInLane extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isDontneedexecuteinlane.value
  }

  object reverse extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isReverse.value
  }

  object average extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isAverage.value
  }

  object ffo extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isFfo.value
  }

  object popCount extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isPopcount.value
  }

  object specialSlot extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isSpecialslot.value
  }

  object float extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isFloat.value
  }

  object floatMul extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isFloatmul.value
  }

  object orderReduce extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isOrderreduce.value
  }

  object zvbb extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isZvbb.value
  }

  object zvma extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isZvma.value
  }

  object topUop extends T1TopUopField {
    override def genTable(pattern: T1DecodePattern): BitPat = pattern.topUop.value match {
      case _: TopT0.type  => BitPat("b00000")
      case _: TopT1.type  => BitPat("b00001")
      case _: TopT2.type  => BitPat("b00010")
      case _: TopT3.type  => BitPat("b00011")
      case _: TopT4.type  => BitPat("b00100")
      case _: TopT5.type  => BitPat("b00101")
      case _: TopT6.type  => BitPat("b00110")
      case _: TopT7.type  => BitPat("b00111")
      case _: TopT8.type  => BitPat("b01000")
      case _: TopT9.type  => BitPat("b01001")
      case _: TopT10.type => BitPat("b01010")
      case _: TopT11.type => BitPat("b01011")
      case _: TopT12.type => BitPat("b01100")
      case _: TopT13.type => BitPat("b01101")
      case _: TopT14.type => BitPat("b01110")
      case _: TopT15.type => BitPat("b01111")
      case _: TopT16.type => BitPat("b10000")
      case _: TopT17.type => BitPat("b10001")
      case _: TopT18.type => BitPat("b10010")
      case _: TopT19.type => BitPat("b10011")
      case _: TopT20.type => BitPat("b10100")
      case _: TopT21.type => BitPat("b10101")
      case _: TopT22.type => BitPat("b10110")
      case _: TopT23.type => BitPat("b10111")
      case _: TopT24.type => BitPat("b11000")
      case _: TopT25.type => BitPat("b11001")
      case _: TopT26.type => BitPat("b11010")
      case _: TopT27.type => BitPat("b11011")
      case _: TopT28.type => BitPat("b11100")
      case _: TopT29.type => BitPat("b11101")
      case _: TopT30.type => BitPat("b11110")
      case _: TopT31.type => BitPat("b11111")
      case _ => BitPat.dontCare(5)
    }
  }

  object uop extends T1UopField {
    override def genTable(pattern: T1DecodePattern): BitPat = pattern.decoderUop.value match {
      case addCase:   AdderUOPType =>
        addCase match {
          case _: addUop0.type  => BitPat("b0000")
          case _: addUop1.type  => BitPat("b0001")
          case _: addUop10.type => BitPat("b1010")
          case _: addUop11.type => BitPat("b1011")
          case _: addUop2.type  => BitPat("b0010")
          case _: addUop3.type  => BitPat("b0011")
          case _: addUop4.type  => BitPat("b0100")
          case _: addUop6.type  => BitPat("b0110")
          case _: addUop7.type  => BitPat("b0111")
          case _: addUop8.type  => BitPat("b1000")
          case _: addUop9.type  => BitPat("b1001")
          case _ => BitPat.dontCare(4)
        }
      case divCase:   DivUOPType   =>
        divCase match {
          case _: divUop0.type  => BitPat("b0000")
          case _: divUop1.type  => BitPat("b0001")
          case _: divUop10.type => BitPat("b1010")
          case _: divUop8.type  => BitPat("b1000")
          case _: divUop9.type  => BitPat("b1001")
          case _ => BitPat.dontCare(4)
        }
      case floatCase: FloatUopType =>
        floatCase match {
          case _: FUT0.type  => BitPat("b0000")
          case _: FUT1.type  => BitPat("b0001")
          case _: FUT10.type => BitPat("b1010")
          case _: FUT12.type => BitPat("b1100")
          case _: FUT13.type => BitPat("b1101")
          case _: FUT14.type => BitPat("b1110")
          case _: FUT2.type  => BitPat("b0010")
          case _: FUT3.type  => BitPat("b0011")
          case _: FUT4.type  => BitPat("b0100")
          case _: FUT5.type  => BitPat("b0101")
          case _: FUT6.type  => BitPat("b0110")
          case _: FUT7.type  => BitPat("b0111")
          case _: FUT8.type  => BitPat("b1000")
          case _: FUT9.type  => BitPat("b1001")
          case _ => BitPat.dontCare(4)
        }
      case logicCase: LogicUopType =>
        logicCase match {
          case _: logicUop0.type => BitPat("b0000")
          case _: logicUop1.type => BitPat("b0001")
          case _: logicUop2.type => BitPat("b0010")
          case _: logicUop4.type => BitPat("b0100")
          case _: logicUop5.type => BitPat("b0101")
          case _: logicUop6.type => BitPat("b0110")
          case _: logicUop8.type => BitPat("b1000")
          case _: logicUop9.type => BitPat("b1001")
          case _ => BitPat.dontCare(4)
        }
      case mulCase:   MulUOPType   =>
        mulCase match {
          case _: mulUop0.type  => BitPat("b0000")
          case _: mulUop1.type  => BitPat("b0001")
          case _: mulUop10.type => BitPat("b1010")
          case _: mulUop14.type => BitPat("b1110")
          case _: mulUop3.type  => BitPat("b0011")
          case _: mulUop5.type  => BitPat("b0101")
          case _ => BitPat.dontCare(4)
        }
      case otherCase: OtherUopType =>
        otherCase match {
          case _: otherUop0.type => BitPat("b0000")
          case _: otherUop1.type => BitPat("b0001")
          case _: otherUop2.type => BitPat("b0010")
          case _: otherUop3.type => BitPat("b0011")
          case _: otherUop4.type => BitPat("b0100")
          case _: otherUop5.type => BitPat("b0101")
          case _: otherUop6.type => BitPat("b0110")
          case _: otherUop7.type => BitPat("b0111")
          case _: otherUop8.type => BitPat("b1000")
          case _: otherUop9.type => BitPat("b1001")
          case _ => BitPat.dontCare(4)
        }
      case shiftCase: ShiftUopType =>
        shiftCase match {
          case _: shiftUop0.type => BitPat("b0000")
          case _: shiftUop1.type => BitPat("b0001")
          case _: shiftUop2.type => BitPat("b0010")
          case _: shiftUop4.type => BitPat("b0100")
          case _: shiftUop6.type => BitPat("b0110")
          case _ => BitPat.dontCare(4)
        }
      case zeroCase:  ZeroUOPType  =>
        zeroCase match {
          case _: zeroUop0.type => BitPat("b0000")
          case _ => BitPat.dontCare(4)
        }
      case zvbbCase:  ZvbbUOPType  =>
        zvbbCase match {
          case _: zvbbUop0.type => BitPat("b0000") // brev
          case _: zvbbUop1.type => BitPat("b0001") // brev8
          case _: zvbbUop2.type => BitPat("b0010") // rev8
          case _: zvbbUop3.type => BitPat("b0011") // clz
          case _: zvbbUop4.type => BitPat("b0100") // ctz
          case _: zvbbUop5.type => BitPat("b0101") // rol
          case _: zvbbUop6.type => BitPat("b0110") // ror
          case _: zvbbUop7.type => BitPat("b0111") // wsll
          case _: zvbbUop8.type => BitPat("b1000") // andn
          case _: zvbbUop9.type => BitPat("b1001") // pop
          case _ => BitPat.dontCare(4)
        }
      case _ => BitPat.dontCare(4)
    }
  }

  object fpExecutionType extends T1fpExecutionTypeUopField {
    override def genTable(pattern: T1DecodePattern): BitPat = pattern.fpExecutionType match {
      case FpExecutionType.Compare => BitPat("b10")
      case FpExecutionType.MA      => BitPat("b00")
      case FpExecutionType.Other   => BitPat("b11")
      case FpExecutionType.Nil     => BitPat.dontCare(2)
    }
  }

  object maskPipeType extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isMaskPip.value
  }

  object writeCount extends BoolField {
    override def getTriState(pattern: T1DecodePattern): TriState = pattern.isWriteCount.value
  }

  object maskPipeUop extends T1TopUopField {
    override def genTable(pattern: T1DecodePattern): BitPat = pattern.maskPipeUop.value match {
      case _: MaskUop0.type  => BitPat("b00000")
      case _: MaskUop1.type  => BitPat("b00001")
      case _: MaskUop2.type  => BitPat("b00010")
      case _: MaskUop3.type  => BitPat("b00011")
      case _: MaskUop4.type  => BitPat("b00100")
      case _: MaskUop5.type  => BitPat("b00101")
      case _: MaskUop6.type  => BitPat("b00110")
      case _: MaskUop7.type  => BitPat("b00111")
      case _: MaskUop8.type  => BitPat("b01000")
      case _: MaskUop9.type  => BitPat("b01001")
      case _: MaskUop10.type => BitPat("b01010")
      case _: MaskUop11.type => BitPat("b01011")
      case _ => BitPat.dontCare(chiselType.getWidth)
    }
  }

  def allFields(param: DecoderParam):        Seq[T1DecodeFiled[_ >: Bool <: UInt]] = Seq(
    logic,
    adder,
    shift,
    multiplier,
    divider,
    multiCycle,
    other,
    maskPipeType,
    unsigned0,
    unsigned1,
    itype,
    nr,
    red,
    // top only
    widenReduce,
    targetRd,
    slid,
    gather,
    gather16,
    compress,
    unOrderWrite,
    // top uop
    extend,   // top uop
    mv,       // top uop
    iota,     // top uop
    uop,
    maskLogic,
    maskDestination,
    maskSource,
    readOnly,
    vwmacc,
    saturate,
    special,
    maskUnit,
    crossWrite,
    crossRead,
    // state
    sWrite,
    // sRead1 -> vType
    vtype,
    sReadVD,
    scheduler,
    dontNeedExecuteInLane,
    reverse,  // uop
    average,  // uop
    ffo,      // todo: add mask select -> top uop
    popCount, // top uop add, red, uop popCount
    topUop,
    maskPipeUop,
    writeCount,
    specialSlot
  ) ++ {
    if (param.fpuEnable)
      Seq(
        float,
        fpExecutionType,
        floatMul,
        orderReduce
      )
    else Seq()
  } ++ {
    if (param.zvbbEnable)
      Seq(
        zvbb
      )
    else Seq()
  } ++ {
    if (param.useXsfmm)
      Seq(
        zvma
      )
    else Seq()
  }
  def allDecodePattern(param: DecoderParam): Seq[T1DecodePattern]                  =
    param.allInstructions.map(T1DecodePattern(_, param)).toSeq.sortBy(_.instruction.name)

  def decodeTable(param: DecoderParam): DecodeTable[T1DecodePattern] =
    new DecodeTable[T1DecodePattern](allDecodePattern(param), allFields(param))

  def decode(param: DecoderParam): UInt => DecodeBundle = decodeTable(param).decode
  def bundle(param: DecoderParam): DecodeBundle         = decodeTable(param).bundle
}

trait FieldName {
  def name: String = this.getClass.getSimpleName.replace("$", "")
}

case class SpecialAux(name: String, vs: Int, value: String)
case class SpecialMap(name: String, vs: Int, data: Map[String, String])
case class SpecialAuxInstr(instrName: String, vs: Int, value: String, name: String)
case class Op(
  tpe:     String,
  funct6:  String,
  tpeOp2:  String,
  funct3:  String,
  name:    String,
  special: Option[SpecialAux],
  notLSU:  Boolean,
  vd:      String,
  opcode:  String)
    extends DecodePattern {
  // include 32 bits: funct6 + vm + vs2 + vs1 + funct3 + vd + opcode
  def bitPat: BitPat = BitPat(
    "b" +
      // funct6
      funct6 +
      // ? for vm
      "?" +
      // vs2
      (if (special.isEmpty || special.get.vs == 1) "?????" else special.get.value) +
      // vs1
      (if (special.isEmpty || special.get.vs == 2) "?????" else special.get.value) +
      // funct3
      funct3 +
      vd +
      opcode
  )
}
