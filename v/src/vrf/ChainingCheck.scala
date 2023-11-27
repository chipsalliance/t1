// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package v
import chisel3._
import chisel3.util._


/** read        : 发起读请求的相应信息
 *  readRecord  : 发起读请求的指令的记录
 *  record      : 要做比对的指令的记录
 *              todo: 维护冲突表,免得每次都要算一次
 */
class ChainingCheck(val parameter: VRFParam) extends Module {
  val read: VRFReadRequest = IO(Input(new VRFReadRequest(parameter.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits)))
  val readRecord: VRFWriteReport = IO(Input(new VRFWriteReport(parameter)))
  val record: ValidIO[VRFWriteReport] = IO(Flipped(Valid(new VRFWriteReport(parameter))))
  val recordValid: Bool = IO(Input(Bool()))
  val checkResult: Bool = IO(Output(Bool()))

  // 先看新老
  val older: Bool = instIndexL(read.instructionIndex, record.bits.instIndex)
  val sameInst: Bool = read.instructionIndex === record.bits.instIndex
  val readOH: UInt = UIntToOH((read.vs ## read.offset)(4, 0))
  val hitElement: Bool = (readOH & record.bits.elementMask) === 0.U
  val vd: UInt = readRecord.vd.bits

  val raw: Bool = record.bits.vd.valid && (read.vs(4, 3) === record.bits.vd.bits) && hitElement
  val waw: Bool = readRecord.vd.valid && record.bits.vd.valid && readRecord.vd.bits === record.bits.vd.bits &&
    hitElement
  val war: Bool = readRecord.vd.valid &&
    (((vd === record.bits.vs1.bits) && record.bits.vs1.valid) || (vd === record.bits.vs2) ||
      ((vd === record.bits.vd.bits) && record.bits.ma)) && hitElement
  checkResult := !((!older && (waw || raw || war)) && !sameInst && recordValid)
}
