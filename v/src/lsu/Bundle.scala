// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package lsu

import chisel3._
import chisel3.util._
import v._

class LSUBaseStatus extends Bundle {
  /** indicate this MSHR is idle. */
  val idle: Bool = Bool()

  /** indicate this is the last cycle for a MSHR */
  val last: Bool = Bool()
  /** the current instruction in this MSHR. */

  /** the current instruction in this MSHR. */
  val instructionIndex: UInt = UInt(3.W)

  val changeMaskGroup: Bool = Bool()
}

class SimpleAccessStatus(laneNumber: Int) extends LSUBaseStatus {
  /** the MSHR finished the current offset group,
   * need to notify Scheduler for next index group.
   */
  val offsetGroupEnd: Bool = Bool()

  /** the current lane that this MSHR is accessing. */
  val targetLane: UInt = UInt(laneNumber.W)

  /** wait for the fault for fault-only-first instruction. */
  val waitFirstResponse: Bool = Bool()
}

class StoreStatus(bankSize: Int) extends LSUBaseStatus {
  // cache line 的发送不能被打断
  val releasePort: Vec[Bool] = Vec(bankSize, Bool())
}

class MSHRStage0Bundle(param: MSHRParam) extends Bundle {
  // 读的相关
  val readVS: UInt = UInt(param.regNumBits.W)
  // 访问寄存器的 offset, 代表第几个32bit
  val offsetForVSInLane: UInt = UInt(param.vrfOffsetBits.W)

  // 由于 stride 需要乘, 其他的类型也有相应的 offset, 所以我们先把 offset 算出来
  val addressOffset: UInt = UInt(param.paWidth.W)
  val segmentIndex:  UInt = UInt(3.W)
  val offsetForLane: UInt = UInt(log2Ceil(param.laneNumber).W)

  // 在一个组内的offset
  val indexInGroup: UInt = UInt(param.maxOffsetPerLaneAccessBits.W)
}

class SimpleAccessStage1(param: MSHRParam) extends Bundle {
  val indexInMaskGroup: UInt = UInt(param.maxOffsetPerLaneAccessBits.W)
  val segmentIndex:     UInt = UInt(3.W)

  // 访问l2的地址
  val address: UInt = UInt(param.paWidth.W)
}
