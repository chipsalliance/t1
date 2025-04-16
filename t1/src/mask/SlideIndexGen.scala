// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.util._
import org.chipsalliance.t1.rtl.decoder.Decoder

class SlideIndexGen(parameter: T1Parameter) extends Module {
  val newInstruction: Bool = IO(Input(Bool()))

  val instructionReq: MaskUnitInstReq = IO(Input(new MaskUnitInstReq(parameter)))

  val indexDeq: DecoupledIO[MaskUnitReadState] = IO(Decoupled(new MaskUnitReadState(parameter)))

  val lgNumber:       Int  = parameter.laneParam.laneNumberBits
  val slideGroupOut:  UInt = IO(Output(UInt((parameter.laneParam.vlMaxBits - lgNumber).W)))
  val slideMaskInput: UInt = IO(Input(UInt(parameter.laneNumber.W)))

  val lastFire:         Bool = Wire(Bool())
  val InstructionValid: Bool = RegEnable(newInstruction, false.B, newInstruction || lastFire)
  val vl:               UInt = instructionReq.vl

  val isSlide: Bool = instructionReq.decodeResult(Decoder.topUop) === BitPat("b000??")
  val slideUp: Bool = instructionReq.decodeResult(Decoder.topUop)(0)
  val slide1:  Bool = instructionReq.decodeResult(Decoder.topUop)(1)

  // each slide group laneNumber element
  val slideGroup = RegInit(0.U((parameter.laneParam.vlMaxBits - lgNumber).W))
  val vlTail: UInt = changeUIntSize(vl, lgNumber)
  val lastSlideGroup = (vl >> lgNumber).asUInt - !changeUIntSize(vl, lgNumber).orR
  val lastValidVec   = (~scanLeftOr(UIntToOH(vlTail))).asUInt

  val groupVlValid   = maskEnable(slideGroup === lastSlideGroup && vlTail.orR, lastValidVec)
  val groupMaskValid = maskEnable(instructionReq.maskType, slideMaskInput)

  val validVec = groupVlValid & groupMaskValid

  val lastElementValid: UInt = ((groupVlValid >> 1).asUInt ^ groupVlValid) & groupMaskValid
  val replaceWithVs1:   UInt = Mux1H(
    Seq(
      (slideGroup === 0.U && slide1 && slideUp)             -> (1.U(parameter.laneNumber.W) & groupMaskValid(0)),
      (slideGroup === lastSlideGroup && slide1 && !slideUp) -> lastElementValid
    )
  ).asUInt

  lastFire      := slideGroup === lastSlideGroup && indexDeq.fire
  slideGroupOut := slideGroup
  when(newInstruction || indexDeq.fire) {
    slideGroup := Mux(newInstruction, 0.U, slideGroup + 1.U)
  }

  val slideValue: UInt = Mux(slide1, 1.U, instructionReq.readFromScala)
  // Positive and negative select
  val PNSelect:   UInt = Mux(slideUp, (~slideValue).asUInt, slideValue)

  val baseIndex:   UInt = (slideGroup << lgNumber).asUInt + PNSelect + slideUp
  val lagerThanVL: Bool = (slideValue >> parameter.laneParam.vlMaxBits).asUInt.orR

  def indexAnalysis(sewInt: Int)(elementIndex: UInt, vlmul: UInt, valid: Bool): Seq[UInt] = {
    val intLMULInput: UInt = (1.U << vlmul(1, 0)).asUInt
    val positionSize    = parameter.laneParam.vlMaxBits - 1
    val allDataPosition = (elementIndex << sewInt).asUInt
    val dataPosition    = changeUIntSize(allDataPosition, positionSize)

    val dataPathBaseBits = log2Ceil(parameter.datapathWidth / 8)
    val dataOffset: UInt = dataPosition(dataPathBaseBits - 1, 0)
    val accessLane =
      if (parameter.laneNumber > 1)
        dataPosition(log2Ceil(parameter.laneNumber) + dataPathBaseBits - 1, dataPathBaseBits)
      else 0.U(1.W)
    // 32 bit / group
    val dataGroup  = (dataPosition >> (log2Ceil(parameter.laneNumber) + dataPathBaseBits)).asUInt
    val offsetWidth: Int = parameter.laneParam.vrfParam.vrfOffsetBits
    val offset            = dataGroup(offsetWidth - 1, 0)
    val accessRegGrowth   = (dataGroup >> offsetWidth).asUInt
    val decimalProportion = offset ## accessLane
    // 1/8 register
    val decimal           = decimalProportion(decimalProportion.getWidth - 1, 0.max(decimalProportion.getWidth - 3))

    /** elementIndex needs to be compared with vlMax(vLen * lmul /sew) This calculation is too complicated We can change
      * the angle. Calculate the increment of the read register and compare it with lmul to know whether the index
      * exceeds vlMax. vlmul needs to distinguish between integers and floating points
      */
    val overlap      =
      (vlmul(2) && decimal >= intLMULInput(3, 1)) ||
        (!vlmul(2) && accessRegGrowth >= intLMULInput) ||
        (allDataPosition >> log2Ceil(parameter.vLen)).asUInt.orR
    val unChange     = slideUp && (elementIndex.asBools.last || lagerThanVL)
    val elementValid = valid && !unChange
    val notNeedRead  = overlap || !elementValid || lagerThanVL || unChange
    val reallyGrowth: UInt = changeUIntSize(accessRegGrowth, 3)
    Seq(dataOffset, accessLane, offset, reallyGrowth, notNeedRead, elementValid)
  }

  val sew1H: UInt = UIntToOH(instructionReq.sew)(2, 0)

  val indexVec = Seq.tabulate(parameter.laneNumber) { index =>
    val readIndex = baseIndex + index.U(lgNumber.W)
    val checkResult: Seq[Seq[UInt]] = Seq(0, 1, 2).map { sewInt =>
      indexAnalysis(sewInt)(readIndex, instructionReq.vlmul, validVec(index))
    }

    val dataOffset   = Mux1H(sew1H, checkResult.map(_.head))
    val accessLane   = Mux1H(sew1H, checkResult.map(_(1)))
    val offset       = Mux1H(sew1H, checkResult.map(_(2)))
    val reallyGrowth = Mux1H(sew1H, checkResult.map(_(3)))
    val notNeedRead  = Mux1H(sew1H, checkResult.map(_(4)))
    val elementValid = Mux1H(sew1H, checkResult.map(_(5)))
    Seq(dataOffset, accessLane, offset, reallyGrowth, notNeedRead, elementValid)
  }

  indexDeq.valid               := InstructionValid && isSlide
  indexDeq.bits                := DontCare
  // 0: dataOffset, 1:  accessLane,
  // 2: offset, 3: reallyGrowth,
  // 4: notNeedRead, 5: elementValid
  indexDeq.bits.needRead       := VecInit(indexVec.map(!_(4))).asUInt & (~replaceWithVs1).asUInt
  indexDeq.bits.elementValid   := VecInit(indexVec.map(_(5))).asUInt | replaceWithVs1
  indexDeq.bits.replaceVs1     := replaceWithVs1
  indexDeq.bits.readOffset     := VecInit(indexVec.map(_(2))).asUInt
  indexDeq.bits.accessLane     := VecInit(indexVec.map(_(1)))
  indexDeq.bits.vsGrowth       := VecInit(indexVec.map(_(3)))
  indexDeq.bits.executeGroup   := slideGroup
  indexDeq.bits.readDataOffset := VecInit(indexVec.map(_.head)).asUInt
  indexDeq.bits.last           := slideGroup === lastSlideGroup
}
