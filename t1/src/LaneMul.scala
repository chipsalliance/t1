// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, Instance, Instantiate}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
import org.chipsalliance.t1.rtl.decoder.{BoolField, Decoder}
import org.chipsalliance.t1.rtl.vfu.{Abs32, VectorAdder64, VectorMultiplier32Unsigned}

object LaneMulParam {
  implicit def rw: upickle.default.ReadWriter[LaneMulParam] = upickle.default.macroRW
}

/** @param dataPathWidth width of data path, can be 32 or 64, decides the memory bandwidth. */
case class LaneMulParam(datapathWidth: Int, latency: Int) extends VFUParameter with SerializableModuleParameter {
  val respWidth:   Int       = datapathWidth
  val sourceWidth: Int       = datapathWidth
  val decodeField: BoolField = Decoder.multiplier
  val inputBundle  = new LaneMulReq(this)
  val outputBundle = new LaneMulResponse(this)
}

class LaneMulReq(parameter: LaneMulParam) extends VFUPipeBundle {
  val src:      Vec[UInt] = Vec(3, UInt(parameter.sourceWidth.W))
  val opcode:   UInt      = UInt(4.W)
  val saturate: Bool      = Bool()
  val vSew:     UInt      = UInt(2.W)
  val sign0:    Bool      = Bool()
  val sign:     Bool      = Bool()

  /** Rounding mode register */
  val vxrm: UInt = UInt(2.W)
}

class LaneMulResponse(parameter: LaneMulParam) extends VFUPipeBundle {
  val data:  UInt = UInt(parameter.respWidth.W)
  val vxsat: Bool = Bool()
}

@instantiable
class LaneMul(val parameter: LaneMulParam) extends VFUModule(parameter) with SerializableModule[LaneMulParam] {
  val response: LaneMulResponse = Wire(new LaneMulResponse(parameter))
  val request:  LaneMulReq      = connectIO(response).asTypeOf(parameter.inputBundle)

  val sew1H:    UInt =
    RegEnable(UIntToOH(requestIO.bits.asTypeOf(request).vSew)(2, 0), 0.U(3.W), requestIO.fire)
  val vxrm1H:   UInt = UIntToOH(request.vxrm)
  // ["mul", "ma", "ms", "mh"]
  val opcode1H: UInt = UIntToOH(request.opcode(1, 0))
  val ma:       Bool = opcode1H(1) || opcode1H(2)
  val asAddend = request.opcode(2)
  val negative = request.opcode(3)

  // vs1 一定是被乘数
  val mul0:            UInt      = request.src.head
  val mulAbs0:         UInt      = Abs32(mul0, sew1H)
  val mul0InputSelect: UInt      = Mux(request.sign0, mulAbs0, mul0)
  val mul0Sign:        Seq[Bool] = cutUInt(mul0, 8).map(_(7) && request.sign0)

  // 另一个乘数
  val mul1:            UInt      = Mux(asAddend || !ma, request.src(1), request.src.last)
  val mulAbs1:         UInt      = Abs32(mul1, sew1H)
  val mul1InputSelect: UInt      = Mux(request.sign || (ma && !asAddend), mulAbs1, mul1)
  val mul1Sign:        Seq[Bool] = cutUInt(mul1, 8).map(_(7) && request.sign)

  // 加数
  val addend: UInt = Mux1H(
    Seq(
      (ma && asAddend)  -> request.src.last,
      (ma && !asAddend) -> request.src(1)(parameter.datapathWidth - 1, 0)
    )
  )

  val fusionMultiplier: Instance[VectorMultiplier32Unsigned] = Instantiate(new VectorMultiplier32Unsigned)
  fusionMultiplier.a   := mul0InputSelect
  fusionMultiplier.b   := mul1InputSelect
  fusionMultiplier.sew := sew1H

  val multiplierSum:   UInt = fusionMultiplier.multiplierSum
  val multiplierCarry: UInt = fusionMultiplier.multiplierCarry

  val sumVec   = cutUInt(multiplierSum, 16)
  val carryVec = cutUInt(multiplierCarry, 16)

  val MSBBlockVec: UInt = true.B ## sew1H(0) ## !sew1H(2) ## sew1H(0)
  // sew = 0 -> 1H: 001 -> 1111
  // sew = 1 -> 1H: 010 -> 0101
  // sew = 2 -> 1H: 100 -> 0001
  val LSBBlockVec   = sew1H(0) ## !sew1H(2) ## sew1H(0) ## true.B
  // a > 0 b > 0 => -(a * b) <=> -(-a * -b) <=> +(-a * b)
  val negativeTag   = VecInit(mul0Sign.zip(mul1Sign).map { case (s0, s1) => s0 ^ s1 ^ negative })
  // negative: - (a * b) + c => -(Cab + Sab) + c => (~Cab + ~Sab + 2) + c => (Cab + Sab) + (c + 2)
  // sew = 0 -> s3_s2_s1_s0
  // sew = 1 -> s3_s3_s1_s1
  // sew = 2 -> s3_s3_s3_s3
  val negativeBlock = negativeTag(3) ##
    Mux(sew1H(0), negativeTag(2), negativeTag(3)) ##
    Mux(sew1H(2), negativeTag(3), negativeTag(1)) ##
    Mux1H(sew1H, Seq(negativeTag(0), negativeTag(1), negativeTag(3)))

  val addendDataVec: Vec[UInt] = cutUInt(addend, 8)
  val zeroByte:      UInt      = Fill(8, false.B)
  val zeroExtend:    UInt      = Fill(7, false.B)
  // addendDataVec: d3_d2_d1_d0
  // 0 extend -> s3 = s2 = s1 = s0 = 0b00000000
  // s3_d3_s2_d2_s1_d1_s0_d0
  // s3_s3_d3_d2_s1_s1_d1_d0
  // s3_s3_s3_s3_d3_d2_d1_d0
  val addendExtend    = zeroByte ##
    Mux(sew1H(0), addendDataVec(3), zeroByte) ##
    Mux(sew1H(1), addendDataVec(3), zeroByte) ##
    Mux(!sew1H(2), addendDataVec(2), zeroByte) ##
    Mux(sew1H(2), addendDataVec(3), zeroByte) ##
    Mux1H(sew1H, Seq(addendDataVec(1), zeroByte, addendDataVec(2))) ##
    Mux(sew1H(0), zeroByte, addendDataVec(1)) ## addendDataVec(0)
  val addendExtendVec = cutUInt(addendExtend, 16)

  val blockCsaCarry: Vec[Bool]         = Wire(Vec(4, Bool()))
  val add2Carry:     Vec[Bool]         = Wire(Vec(4, Bool()))
  val adderInput:    Seq[(UInt, UInt)] = sumVec.zipWithIndex.map { case (sum, index) =>
    val carry: UInt = carryVec(index)
    val isMSB: Bool = MSBBlockVec(index)
    val isLSB: Bool = LSBBlockVec(index)
    val negativeMul = negativeBlock(index)
    val needAdd2    = negativeMul && isLSB
    val previousAdd2Carry: Bool = if (index == 0) false.B else add2Carry(index - 1)
    val pickPreviousAdd2Carry = !isLSB && previousAdd2Carry
    val addCorrection         = addendExtendVec(index) +& (needAdd2 ## pickPreviousAdd2Carry)
    val csaAddInput: UInt = addCorrection(15, 0)
    add2Carry(index) := addCorrection(16)
    val sumSelect    = Mux(negativeMul, (~sum).asUInt, sum)
    val carrySelect  = Mux(negativeMul, (~carry).asUInt, carry)
    val (csaS, csaC) = csa32(sumSelect, carrySelect, csaAddInput)
    blockCsaCarry(index) := csaC(15)
    // Carry from previous data block
    val previousCarry     = if (index == 0) false.B else blockCsaCarry(index - 1)
    val pickPreviousCarry = !isLSB && previousCarry
    (csaS, csaC(14, 0) ## pickPreviousCarry)
  }

  val adder64: Instance[VectorAdder64] = Instantiate(new VectorAdder64)
  adder64.a   := VecInit(adderInput.map(_._1)).asUInt
  adder64.b   := VecInit(adderInput.map(_._2)).asUInt
  adder64.cin := 0.U
  adder64.sew := sew1H ## false.B
  val adderCarry:     UInt      = adder64.cout
  val adderResultVec: Vec[UInt] = cutUInt(adder64.z, 16)
  val notZeroVec:     UInt      = Wire(UInt(4.W))

  val expectedSignVec:         Vec[Bool]         = Wire(Vec(4, Bool()))
  // signVec -> s3_s2_s1_s0
  // sew = 8  -> s3_s2_s1_s0
  // sew = 16 -> s3_s3_s1_s1
  // sew = 32 -> s3_s3_s3_s3
  val expectedSignForBlockVec: UInt              = expectedSignVec(3) ##
    Mux(sew1H(0), expectedSignVec(2), expectedSignVec(3)) ##
    Mux(sew1H(2), expectedSignVec(3), expectedSignVec(1)) ##
    Mux1H(sew1H, Seq(expectedSignVec(0), expectedSignVec(1), expectedSignVec(3)))
  val resultSignVec:           Vec[Bool]         = Wire(Vec(4, Bool()))
  val attributeVec:            Seq[(Bool, UInt)] = adderResultVec.zipWithIndex.map { case (data, index) =>
    val sourceSign0 = cutUInt(mul0, 8)(index)(7)
    val sourceSign1 = cutUInt(mul1, 8)(index)(7)
    val isMSB: Bool = MSBBlockVec(index)
    val notZero           = notZeroVec(index)
    val operation0Sign    = (sourceSign0 && request.sign) ^ negative
    val operation1Sign    = (sourceSign1 && request.sign) ^ negative
    val resultSign        = resultSignVec(index)
    val expectedSigForMul = operation0Sign ^ operation1Sign
    expectedSignVec(index) := expectedSigForMul
    // todo: rounding bit overflow
    val overflow             = (expectedSigForMul ^ resultSign) && notZero
    val expectedSignForBlock = expectedSignForBlockVec(index)
    // max: 0x7fff min: 0x8000
    val overflowSelection    = !(expectedSignForBlock ^ isMSB) ## Fill(7, !expectedSignForBlock)
    (overflow, overflowSelection)
  }

  // todo: Optimize rounding calculations
  val roundResultForSew8: UInt = VecInit(adderResultVec.map { data =>
    val vd1   = data(6)
    val vd    = data(7)
    val vd2OR = data(5, 0).orR
    val roundBits0:  Bool = vd1
    val roundBits1:  Bool = vd1 && (vd2OR || vd)
    val roundBits2:  Bool = !vd && (vd2OR || vd1)
    val roundBits:   Bool = Mux1H(vxrm1H(3) ## vxrm1H(1, 0), Seq(roundBits0, roundBits1, roundBits2))
    // 去掉低位
    val shiftResult: UInt = (data >> 7).asUInt
    (shiftResult + roundBits)(7, 0)
  }).asUInt

  val roundResultForSew16: UInt = VecInit(cutUInt(adder64.z, 32).map { data =>
    val vd1   = data(14)
    val vd    = data(15)
    val vd2OR = data(13, 0).orR
    val roundBits0:  Bool = vd1
    val roundBits1:  Bool = vd1 && (vd2OR || vd)
    val roundBits2:  Bool = !vd && (vd2OR || vd1)
    val roundBits:   Bool = Mux1H(vxrm1H(3) ## vxrm1H(1, 0), Seq(roundBits0, roundBits1, roundBits2))
    // 去掉低位
    val shiftResult: UInt = (data >> 15).asUInt
    (shiftResult + roundBits)(15, 0)
  }).asUInt
  val roundResultForSew32: UInt = {
    val vd1   = adder64.z(30)
    val vd    = adder64.z(31)
    val vd2OR = adder64.z(29, 0).orR
    val roundBits0:  Bool = vd1
    val roundBits1:  Bool = vd1 && (vd2OR || vd)
    val roundBits2:  Bool = !vd && (vd2OR || vd1)
    val roundBits:   Bool = Mux1H(vxrm1H(3) ## vxrm1H(1, 0), Seq(roundBits0, roundBits1, roundBits2))
    // 去掉低位
    val shiftResult: UInt = (adder64.z >> 31).asUInt
    (shiftResult + roundBits)(31, 0)
  }

  val roundingResult: UInt = Mux1H(sew1H, Seq(roundResultForSew8, roundResultForSew16, roundResultForSew32))
  val roundingResultVec = cutUInt(roundingResult, 8)
  resultSignVec.zip(roundingResultVec).foreach { case (s, d) => s := d(7) }
  val roundingResultOrR = VecInit(roundingResultVec.map(_.orR)).asUInt
  val orSew16: UInt =
    VecInit(Seq(roundingResultOrR(0) || roundingResultOrR(1), roundingResultOrR(2) || roundingResultOrR(3))).asUInt
  val orSew32: Bool = orSew16.orR
  notZeroVec := Mux1H(
    sew1H,
    Seq(
      roundingResultOrR,
      FillInterleaved(2, orSew16),
      Fill(4, orSew32)
    )
  )

  val overflowTag = attributeVec.map(_._1)
  val overflowSelect: Vec[Bool] = Mux1H(
    sew1H,
    Seq(
      VecInit(overflowTag),
      VecInit(overflowTag(1), overflowTag(1), overflowTag(3), overflowTag(3)),
      VecInit(overflowTag(3), overflowTag(3), overflowTag(3), overflowTag(3))
    )
  )
  val addResultCutByByte = cutUInt(adder64.z, 8)
  // adderResultVec -> d7_d6_d5_d4_d3_d2_d1_d0 -> d0: 1byte
  // sew = 8  -> d7_d5_d3_d1
  // sew = 16 -> d7_d6_d3_d2
  // sew = 32 -> d7_d6_d5_d4
  val mulMSB: UInt = addResultCutByByte(7) ##
    Mux(sew1H(0), addResultCutByByte(5), addResultCutByByte(6)) ##
    Mux(sew1H(2), addResultCutByByte(5), addResultCutByByte(3)) ##
    Mux1H(sew1H, Seq(addResultCutByByte(1), addResultCutByByte(2), addResultCutByByte(4)))
  val msbVec = cutUInt(mulMSB, 8)

  // adderResultVec -> d7_d6_d5_d4_d3_d2_d1_d0 -> d0: 1byte
  // sew = 8  -> d6_d4_d2_d0
  // sew = 16 -> d5_d4_d1_d0
  // sew = 32 -> d3_d2_d1_d0
  val mulLSB = Mux1H(sew1H, Seq(addResultCutByByte(6), addResultCutByByte(5), addResultCutByByte(3))) ##
    Mux(sew1H(2), addResultCutByByte(2), addResultCutByByte(4)) ##
    Mux(sew1H(0), addResultCutByByte(2), addResultCutByByte(1)) ##
    addResultCutByByte(0)
  val lsbVec = cutUInt(mulLSB, 8)

  val overflowData: Seq[UInt] = attributeVec.map(_._2)
  response.data := VecInit(Seq.tabulate(4) { index =>
    val overflow = overflowSelect(index)
    Mux1H(
      Seq(
        (opcode1H(0) && !request.saturate) || ma,
        opcode1H(3),
        request.saturate && !overflow,
        request.saturate && overflow
      ),
      Seq(lsbVec(index), msbVec(index), roundingResultVec(index), overflowData(index))
    )
  }).asUInt

  // todo
  response.vxsat := overflowSelect.asUInt.orR && request.saturate
}
