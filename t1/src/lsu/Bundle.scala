// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lsu

import chisel3._
import chisel3.util._

class LSUBaseStatus(instIndexWidth: Int) extends Bundle {

  /** indicate this MSHR is idle. */
  val idle: Bool = Bool()

  /** indicate this is the last cycle for a MSHR */
  val last: Bool = Bool()

  /** the current instruction in this MSHR. */
  val instructionIndex: UInt = UInt(instIndexWidth.W)

  val changeMaskGroup: Bool = Bool()

  val startAddress: UInt = UInt(32.W)
  val endAddress:   UInt = UInt(32.W)
}

class SimpleAccessStatus(laneNumber: Int, instIndexWidth: Int) extends LSUBaseStatus(instIndexWidth) {

  /** the current lane that this MSHR is accessing. */
  val targetLane: UInt = UInt(laneNumber.W)

  /** wait for the fault for fault-only-first instruction. */
  val waitFirstResponse: Bool = Bool()

  // current instruction will not write vrf
  val isStore: Bool = Bool()

  val isIndexLS: Bool = Bool()
}

class MSHRStage0Bundle(param: MSHRParam) extends Bundle {
  // 读的相关
  val readVS:            UInt         = UInt(param.regNumBits.W)
  // 访问寄存器的 offset, 代表第几个32bit
  val offsetForVSInLane: Option[UInt] = Option.when(param.vrfOffsetBits > 0)(UInt(param.vrfOffsetBits.W))

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
  val address:  UInt = UInt(param.paWidth.W)
  val readData: UInt = UInt(param.datapathWidth.W)
}

class MemRequest(param: MSHRParam) extends Bundle {
  val src:     UInt = UInt(param.cacheLineIndexBits.W)
  val address: UInt = UInt(param.paWidth.W)
}

class MemDataBundle(param: MSHRParam) extends Bundle {
  // todo: DLEN?
  val data:  UInt = UInt((param.lsuTransposeSize * 8).W)
  val index: UInt = UInt(param.cacheLineIndexBits.W)
}

class MemWrite(transposeSize: Int, indexBit: Int, paWidth: Int) extends Bundle {
  // todo: DLEN?
  val data:    UInt = UInt((transposeSize * 8).W)
  val mask:    UInt = UInt(transposeSize.W)
  val index:   UInt = UInt(indexBit.W)
  val address: UInt = UInt(paWidth.W)
}

class SimpleMemRequest(param: MSHRParam) extends Bundle {
  val address: UInt = UInt(param.paWidth.W)
  val size:    UInt = UInt(2.W)
  val source:  UInt = UInt(param.sourceWidth.W)
}

class SimpleMemReadResponse(param: MSHRParam) extends Bundle {
  val data:   UInt = UInt(param.eLen.W)
  val source: UInt = UInt(param.sourceWidth.W)
}

class SimpleMemWrite(param: MSHRParam) extends Bundle {
  val data:    UInt = UInt(param.eLen.W)
  val mask:    UInt = UInt((param.eLen / 8).W)
  val source:  UInt = UInt(8.W)
  val address: UInt = UInt(param.paWidth.W)
  val size:    UInt = UInt(2.W)
}

class LSUToken(parameter: LSUParameter) extends Bundle {
  val offsetGroupRelease: UInt = Output(UInt(parameter.laneNumber.W))
}
