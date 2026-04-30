// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.vrf

import me.jiuyang.zaozi.*
import me.jiuyang.zaozi.default.{*, given}
import me.jiuyang.zaozi.reftpe.*
import me.jiuyang.zaozi.valuetpe.*
import me.jiuyang.stdlib.*
import me.jiuyang.stdlib.default.{*, given}
import org.chipsalliance.t1.rtl.*
import org.llvm.mlir.scalalib.capi.ir.{*, given}

import java.lang.foreign.Arena

/** read : 发起读请求的相应信息 readRecord : 发起读请求的指令的记录 record : 要做比对的指令的记录 todo: 维护冲突表,免得每次都要算一次
  */
class ChainingCheckLayers(parameter: VRFParam) extends LayerInterface(parameter):
  def layers = Seq.empty

class ChainingCheckInterface(parameter: VRFParam) extends HWBundle(parameter):
  val read:        BundleField[VRFReadRequest]          =
    Flipped(new VRFReadRequest(parameter.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits))
  val readRecord:  BundleField[VRFWriteReport]          = Flipped(new VRFWriteReport(parameter))
  val record:      BundleField[ValidIO[VRFWriteReport]] = Flipped(Valid(new VRFWriteReport(parameter)))
  val recordValid: BundleField[Bool]                    = Flipped(Bool())
  val checkResult: BundleField[Bool]                    = Aligned(Bool())

class ChainingCheckProbe(parameter: VRFParam) extends DVBundle[VRFParam, ChainingCheckLayers](parameter)

@generator
object ChainingCheck extends Generator[VRFParam, ChainingCheckLayers, ChainingCheckInterface, ChainingCheckProbe]:
  override def moduleName(parameter: VRFParam): String = "ChainingCheck"

  def architecture(parameter: VRFParam) =
    val io = summon[Interface[ChainingCheckInterface]]

    // 先看新老
    val older    = instIndexLE(io.read.instructionIndex, io.record.bits.instIndex)
    val sameInst = io.read.instructionIndex === io.record.bits.instIndex

    // 3: 8 register
    val readOH = UIntToOH(
      (io.read.vs.asBits ## io.read.offset.asBits).bits(parameter.vrfOffsetBits + 3 - 1, 0).asUInt
    )

    // todo: def
    val elementSizeForOneRegister: Int = parameter.vLen / parameter.datapathWidth / parameter.laneNumber
    val paddingSize:               Int = elementSizeForOneRegister * 8

    // elementMask records the relative position of the relative instruction.
    // Let's calculate the absolute position.
    val maskShift   =
      (io.record.bits.vd.bits.asBits.bits(2, 0) ## 0.U(chiselLog2Ceil(elementSizeForOneRegister)).asBits).asUInt
    val maskBase    = (fill(paddingSize, true.B) ## io.record.bits.elementMask.asBits ## fill(paddingSize, true.B)).asUInt
    val maskShifter = (((maskBase << maskShift) >> paddingSize).asBits.bits(2 * paddingSize - 1, 0)).asUInt
    // mask for vd's group
    val maskForVD   = cutUIntBySize(maskShifter, 2)(0)
    // Due to the existence of segment load, writes may cross register groups
    // So we need the mask of the previous set of registers
    val maskForVD1  = cutUIntBySize(maskShifter, 2)(1)

    val vdGroup  = io.record.bits.vd.bits.asBits.bits(4, 3).asUInt
    val vdGroup1 = ((vdGroup + 1.U).asBits.bits(vdGroup.width - 1, 0)).asUInt
    val vsGroup  = io.read.vs.asBits.bits(4, 3).asUInt
    val hitVd    = ((readOH.asBits & maskForVD.asBits).asUInt === 0.U(readOH.width)) & (vsGroup === vdGroup)
    val hitVd1   = ((readOH.asBits & maskForVD1.asBits).asUInt === 0.U(readOH.width)) & (vsGroup === vdGroup1)

    val raw = io.record.bits.vd.valid & (hitVd | hitVd1)
    io.checkResult := !(!older & raw & !sameInst & io.recordValid)
