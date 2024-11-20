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
  val readOH: UInt = UIntToOH((read.vs ## read.offset)(parameter.vrfOffsetBits + 3 - 1, 0))

  // todo: def
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

  val hitVd:  Bool = (readOH & maskForVD) === 0.U && read.vs(4, 3) === record.bits.vd.bits(4, 3)
  val hitVd1: Bool = (readOH & maskForVD1) === 0.U && read.vs(4, 3) === (record.bits.vd.bits(4, 3) + 1.U)

  val raw: Bool = record.bits.vd.valid && (hitVd || hitVd1)
  checkResult := !(!older && raw && !sameInst && recordValid)
}
