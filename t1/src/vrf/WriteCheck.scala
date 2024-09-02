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

  // this element in record not execute
  val notHitMaskVd: Bool = (checkOH & record.bits.elementMask) === 0.U
  val waw:          Bool = record.bits.vd.valid && check.vd(4, 3) === record.bits.vd.bits(4, 3) && notHitMaskVd
  // inst eg: vadd v0, v1, v1 (lmul = 1)
  // We only recorded vd-related masks.
  // 0 base: 11111111111111xx eg vs = 0 off=2
  // As above, using vd as the perspective,
  // we will access the lowest two elements of the register group where vd is located.
  // But from the perspective of vs1:
  // 1 base: 111111111111xx11 eg vs = 1 off=2
  // Apparently. Our mask has shifted
  // 0 base => 1 base << (1 * off)
  // we need vd%8 base => vs1%8 base => vd base mask << (vs1 - vd) * off
  // => vd base mask >> 8 * off << (8 + vs1 - vd) * off
  // => vd base mask << (8 + vs1 - vd) * off >> 8 * off
  val vs1Mask:      UInt = (((-1.S(parameter.elementSize.W)).asUInt ## record.bits.elementMask) <<
    ((8.U + record.bits.vs1.bits(2, 0) - record.bits.vd.bits(2, 0)) << parameter.vrfOffsetBits).asUInt).asUInt(
    2 * 8 * parameter.singleGroupSize - 1,
    8 * parameter.singleGroupSize
  )
  val notHitVs1:    Bool = (checkOH & vs1Mask) === 0.U
  val war1:         Bool = record.bits.vs1.valid && check.vd(4, 3) === record.bits.vs1.bits(4, 3) && notHitVs1
  val vs2Mask:      UInt = (((-1.S(parameter.elementSize.W)).asUInt ## record.bits.elementMask) <<
    ((8.U + record.bits.vs2(2, 0) - record.bits.vd.bits(2, 0)) << parameter.vrfOffsetBits).asUInt).asUInt(
    2 * 8 * parameter.singleGroupSize - 1,
    8 * parameter.singleGroupSize
  )
  val notHitVs2:    Bool = (checkOH & vs2Mask) === 0.U
  val war2:         Bool = check.vd(4, 3) === record.bits.vs2(4, 3) && notHitVs2
  checkResult := !((!older && (waw || war1 || war2)) && !sameInst && record.valid)
}
