// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.vrf

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._
import org.chipsalliance.t1.rtl._

@instantiable
class WriteCheck(val parameter: VRFParam) extends Module {
  @public
  val check = IO(
    Input(
      new LSUWriteCheck(
        parameter.regNumBits,
        parameter.vrfOffsetBits,
        parameter.instructionIndexBits
      )
    )
  )
  @public
  val record:      ValidIO[VRFWriteReport] = IO(Flipped(Valid(new VRFWriteReport(parameter))))
  @public
  val recordValid: Bool                    = IO(Input(Bool()))
  @public
  val checkResult: Bool                    = IO(Output(Bool()))

  // 先看新老
  val older:    Bool = instIndexLE(check.instructionIndex, record.bits.instIndex)
  val sameInst: Bool = check.instructionIndex === record.bits.instIndex
  val checkOH:  UInt = UIntToOH((check.vd ## check.offset)(parameter.vrfOffsetBits + 3 - 1, 0))

  val elementSizeForOneRegister: Int = parameter.vLen / parameter.datapathWidth / parameter.laneNumber
  val paddingSize:               Int = elementSizeForOneRegister * 8

  // elementMask records the relative position of the relative instruction.
  // Let's calculate the absolute position.
  val maskShifter: UInt = (((Fill(paddingSize, true.B) ## record.bits.elementMask ## Fill(paddingSize, true.B))
    << record.bits.vd.bits(2, 0) ## 0.U(log2Ceil(elementSizeForOneRegister).W))
    >> paddingSize).asUInt(2 * paddingSize - 1, 0)
  // mask for vd's group
  val maskForVD:   UInt = cutUIntBySize(maskShifter, 2)(0)
  // Due to the existence of segment load, writes may cross register groups
  // So we need the mask of the previous set of registers
  val maskForVD1:  UInt = cutUIntBySize(maskShifter, 2)(1)

  val hitVd:  Bool = (checkOH & maskForVD) === 0.U && check.vd(4, 3) === record.bits.vd.bits(4, 3)
  val hitVd1: Bool = (checkOH & maskForVD1) === 0.U && check.vd(4, 3) === (record.bits.vd.bits(4, 3) + 1.U)
  val waw:    Bool = record.bits.vd.valid && (hitVd || hitVd1)

  // calculate the absolute position for vs1
  val vs1Mask:   UInt = (((record.bits.elementMask ## Fill(paddingSize, true.B))
    << record.bits.vs1.bits(2, 0) ## 0.U(log2Ceil(elementSizeForOneRegister).W))
    >> paddingSize).asUInt
  val notHitVs1: Bool = (checkOH & vs1Mask) === 0.U
  val war1:      Bool = record.bits.vs1.valid && check.vd(4, 3) === record.bits.vs1.bits(4, 3) && notHitVs1

  // calculate the absolute position for vs2
  val maskShifterForVs2: UInt = (((Fill(paddingSize, true.B) ## record.bits.elementMask ## Fill(paddingSize, true.B))
    << record.bits.vs2(2, 0) ## 0.U(log2Ceil(elementSizeForOneRegister).W))
    >> paddingSize).asUInt(2 * paddingSize - 1, 0)

  // check WAR, record.bits.gather -> Gather reads are not ordered
  val maskForVs2:  UInt = cutUIntBySize(maskShifterForVs2, 2)(0) & Fill(parameter.elementSize, !record.bits.onlyRead)
  val maskForVs21: UInt = cutUIntBySize(maskShifterForVs2, 2)(1)
  val hitVs2:      Bool = ((checkOH & maskForVs2) === 0.U || record.bits.gather) && check.vd(4, 3) === record.bits.vs2(4, 3)
  val hitVs21:     Bool =
    ((checkOH & maskForVs21) === 0.U || record.bits.gather) && check.vd(4, 3) === (record.bits.vs2(4, 3) + 1.U)
  val war2:        Bool = hitVs2 || hitVs21

  checkResult := !((!older && (waw || war1 || war2)) && !sameInst && record.valid)
}
