// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.ipemu.dpi

import chisel3._
import org.chipsalliance.t1.rtl.VRFWriteRequest

case class PeekWriteQueueParameter(regNumBits: Int,
                                   laneNumber: Int,
                                   vrfOffsetBits: Int,
                                   instructionIndexBits: Int,
                                   datapathWidth: Int,
                                   negTriggerDelay: Int)

class PeekWriteQueue(p: PeekWriteQueueParameter) extends DPIModuleLegacy {
  val isImport: Boolean = true
  val clock = dpiTrigger("clock", Input(Bool()))

  val mshrIdx = dpiIn("mshrIdx", Input(UInt(32.W)))
  val writeValid = dpiIn("writeValid", Input(Bool()))

  val data = new VRFWriteRequest(
    p.regNumBits,
    p.vrfOffsetBits,
    p.instructionIndexBits,
    p.datapathWidth
  )
  val data_vd = dpiIn("data_vd", Input(data.vd))
  val data_offset = dpiIn("data_offset", Input(data.offset))
  val data_mask = dpiIn("data_mask", Input(data.mask))
  val data_data = dpiIn("data_data", Input(data.data))
  val data_instruction = dpiIn("data_instructionIndex", Input(data.instructionIndex))

  val targetLane = dpiIn("targetLane", Input(UInt(p.laneNumber.W)))

  override val trigger = s"always @(negedge ${clock.name}) #(${p.negTriggerDelay})"
}
