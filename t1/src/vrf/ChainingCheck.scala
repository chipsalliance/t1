// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.vrf

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._
import org.chipsalliance.t1.rtl._

/** read : 发起读请求的相应信息 readRecord : 发起读请求的指令的记录 record : 要做比对的指令的记录 todo: 维护冲突表,免得每次都要算一次
  */
@instantiable
class ChainingCheck(val parameter: VRFParam) extends Module {
  @public
  val read:        VRFReadRequest          = IO(
    Input(new VRFReadRequest(parameter.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits))
  )
  @public
  val readRecord:  VRFWriteReport          = IO(Input(new VRFWriteReport(parameter)))
  @public
  val record:      ValidIO[VRFWriteReport] = IO(Flipped(Valid(new VRFWriteReport(parameter))))
  @public
  val recordValid: Bool                    = IO(Input(Bool()))
  @public
  val checkResult: Bool                    = IO(Output(Bool()))

  // 先看新老
  val older:    Bool = instIndexLE(read.instructionIndex, record.bits.instIndex)
  val sameInst: Bool = read.instructionIndex === record.bits.instIndex

  // 3: 8 register
  val readOH:     UInt = UIntToOH((read.vs ## read.offset)(parameter.vrfOffsetBits + 3 - 1, 0))
  val hitElement: Bool = (readOH & record.bits.elementMask) === 0.U

  val raw: Bool = record.bits.vd.valid && (read.vs(4, 3) === record.bits.vd.bits(4, 3)) && hitElement
  checkResult := !(!older && raw && !sameInst && recordValid)
}
