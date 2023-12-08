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

  // read.readSource === 3.U -> readBase = 0.U
  val readBase: UInt = Mux1H(
    UIntToOH(read.readSource)(2, 0),
    Seq(readRecord.vs1.bits, readRecord.vs2, readRecord.vd.bits)
  )
  // use for waw | war check, if read success, where will write.
  val willWriteVd: UInt = readRecord.vd.bits(2, 0) + read.vs - readBase
  // tip: Only the oldest instructions will be written across lanes
  val writeOH: UInt = UIntToOH((willWriteVd ## read.offset)(4, 0))
  val writeHitElement: Bool = (writeOH & record.bits.elementMask) === 0.U

  val vdGroup: UInt = readRecord.vd.bits(4, 3)

  val raw: Bool = record.bits.vd.valid && (read.vs(4, 3) === record.bits.vd.bits(4, 3)) && hitElement
  val waw: Bool = readRecord.vd.valid && record.bits.vd.valid &&
    readRecord.vd.bits(4, 3) === record.bits.vd.bits(4, 3) &&
    writeHitElement
  val warSource1: Bool = (vdGroup === record.bits.vs1.bits(4, 3)) && record.bits.vs1.valid
  // Only index type will read vs2
  val warSource2: Bool = vdGroup === record.bits.vs2(4, 3) && (!record.bits.ls || record.bits.indexType)
  // store or ma need read vd
  val warVD: Bool = (vdGroup === record.bits.vd.bits(4, 3)) && (record.bits.ma || record.bits.st)
  val war: Bool = readRecord.vd.valid && (warSource1 || warSource2 || warVD) && writeHitElement
  checkResult := !((!older && (waw || raw || war)) && !sameInst && recordValid)
}
