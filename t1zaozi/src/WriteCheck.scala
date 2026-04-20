// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.vrf

import me.jiuyang.zaozi.*
import me.jiuyang.zaozi.default.{*, given}
import me.jiuyang.zaozi.reftpe.*
import me.jiuyang.zaozi.valuetpe.*
import me.jiuyang.stdlib.*
import me.jiuyang.stdlib.default.{*, given}
import org.chipsalliance.t1.rtl.zvma.*
import org.llvm.mlir.scalalib.capi.ir.{*, given}

import java.lang.foreign.Arena

class WriteCheckLayers(parameter: VRFParam) extends LayerInterface(parameter):
  def layers = Seq.empty

class WriteCheckInterface(parameter: VRFParam) extends HWBundle(parameter):
  val check:       BundleField[LSUWriteCheck]           =
    Flipped(new LSUWriteCheck(parameter.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits))
  val record:      BundleField[ValidIO[VRFWriteReport]] = Flipped(Valid(new VRFWriteReport(parameter)))
  val recordValid: BundleField[Bool]                    = Flipped(Bool())
  val checkResult: BundleField[Bool]                    = Aligned(Bool())

class WriteCheckProbe(parameter: VRFParam) extends DVBundle[VRFParam, WriteCheckLayers](parameter)

@generator
object WriteCheck extends Generator[VRFParam, WriteCheckLayers, WriteCheckInterface, WriteCheckProbe]:
  override def moduleName(parameter: VRFParam): String = "WriteCheck"

  def architecture(parameter: VRFParam) =
    val io = summon[Interface[WriteCheckInterface]]

    // 先看新老
    val older    = instIndexLE(io.check.instructionIndex, io.record.bits.instIndex)
    val sameInst = io.check.instructionIndex === io.record.bits.instIndex
    val checkOH  = UIntToOH(
      (io.check.vd.asBits ## io.check.offset.asBits).bits(parameter.vrfOffsetBits + 3 - 1, 0).asUInt
    )

    val elementSizeForOneRegister: Int = parameter.vLen / parameter.datapathWidth / parameter.laneNumber
    val paddingSize:               Int = elementSizeForOneRegister * 8

    // elementMask records the relative position of the relative instruction.
    // Let's calculate the absolute position.
    val vdShift     =
      (io.record.bits.vd.bits.asBits.bits(2, 0) ## 0.U(chiselLog2Ceil(elementSizeForOneRegister)).asBits).asUInt
    val maskBase    = (fill(paddingSize, true.B) ## io.record.bits.elementMask.asBits ## fill(paddingSize, true.B)).asUInt
    val maskShifter = (((maskBase << vdShift) >> paddingSize).asBits.bits(2 * paddingSize - 1, 0)).asUInt
    // mask for vd's group
    val maskForVD   = cutUIntBySize(maskShifter, 2)(0)
    // Due to the existence of segment load, writes may cross register groups
    // So we need the mask of the previous set of registers
    val maskForVD1  = cutUIntBySize(maskShifter, 2)(1)

    val vdGroup  = io.record.bits.vd.bits.asBits.bits(4, 3).asUInt
    val vdGroup1 = ((vdGroup + 1.U(vdGroup.width)).asBits.bits(vdGroup.width - 1, 0)).asUInt
    val checkVd  = io.check.vd.asBits.bits(4, 3).asUInt
    val hitVd    = ((checkOH.asBits & maskForVD.asBits).asUInt === 0.U(checkOH.width)) & (checkVd === vdGroup)
    val hitVd1   = ((checkOH.asBits & maskForVD1.asBits).asUInt === 0.U(checkOH.width)) & (checkVd === vdGroup1)
    val waw      = io.record.bits.vd.valid & (hitVd | hitVd1)

    // calculate the absolute position for vs1
    val vs1Shift  =
      (io.record.bits.vs1.bits.asBits.bits(2, 0) ## 0.U(chiselLog2Ceil(elementSizeForOneRegister)).asBits).asUInt
    val vs1Base   = (io.record.bits.elementMask.asBits ## fill(paddingSize, true.B)).asUInt
    val vs1Mask   = (((vs1Base << vs1Shift) >> paddingSize).asBits).asUInt
    // Gather16 will read and write lengths mismatch
    val notHitVs1 = ((checkOH.asBits & vs1Mask.asBits).asUInt === 0.U(checkOH.width)) | io.record.bits.unalignedReadVs1
    val war1      = io.record.bits.vs1.valid & (checkVd === io.record.bits.vs1.bits.asBits.bits(4, 3).asUInt) & notHitVs1

    // calculate the absolute position for vs2
    val vs2Shift          =
      (io.record.bits.vs2.asBits.bits(2, 0) ## 0.U(chiselLog2Ceil(elementSizeForOneRegister)).asBits).asUInt
    val vs2Base           = (fill(paddingSize, true.B) ## io.record.bits.elementMask.asBits ## fill(paddingSize, true.B)).asUInt
    val maskShifterForVs2 = (((vs2Base << vs2Shift) >> paddingSize).asBits.bits(2 * paddingSize - 1, 0)).asUInt

    // check WAR, record.bits.gather -> Gather reads are not ordered
    val maskForVs2  =
      (cutUIntBySize(maskShifterForVs2, 2)(0).asBits & fill(parameter.elementSize, !io.record.bits.onlyRead)).asUInt
    val maskForVs21 = cutUIntBySize(maskShifterForVs2, 2)(1)
    val vs2Group    = io.record.bits.vs2.asBits.bits(4, 3).asUInt
    val vs21Group   = ((vs2Group + 1.U(vs2Group.width)).asBits.bits(vs2Group.width - 1, 0)).asUInt
    val hitVs2      = (((checkOH.asBits & maskForVs2.asBits).asUInt === 0.U(
      checkOH.width
    )) | io.record.bits.gather) & (checkVd === vs2Group)
    val hitVs21     = (((checkOH.asBits & maskForVs21.asBits).asUInt === 0.U(
      checkOH.width
    )) | io.record.bits.gather) & (checkVd === vs21Group)
    val war2        = hitVs2 | hitVs21

    io.checkResult := !((!older & (waw | war1 | war2)) & !sameInst & io.record.valid)
