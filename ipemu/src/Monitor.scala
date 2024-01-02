// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.ipemu

import chisel3._
import chisel3.util.experimental.BoringUtils.tapAndRead
import org.chipsalliance.t1.ipemu.dpi._
import org.chipsalliance.t1.rtl.V

class Monitor(dut: V) extends RawModule {
  val clock = IO(Input(Clock()))
  val reset = IO(Input(Bool()))

  // lane vrf write
  dut.laneVec.zipWithIndex.foreach { case (lane, index) =>
    val vrfMonitor = Module(new VrfMonitor(VrfMonitorParameter(3)))
    vrfMonitor.clock.ref := clock.asBool
    vrfMonitor.vrfWriteValid.ref := tapAndRead(lane.vrf.write.valid)
    vrfMonitor.laneIdx.ref := index.U
  }

  // alu occupied
  dut.laneVec.zipWithIndex.foreach { case (lane, index) =>
    val aluMonitor = Module(new AluMonitor(AluMonitorParameter(3)))
    aluMonitor.clock.ref := clock.asBool
    aluMonitor.isAdderOccupied.ref := tapAndRead(lane.executeOccupied(1))
    aluMonitor.isShifterOccupied.ref := tapAndRead(lane.executeOccupied(2))
    aluMonitor.isMultiplierOccupied.ref := tapAndRead(lane.executeOccupied(3))
    aluMonitor.isDividerOccupied.ref := tapAndRead(lane.executeOccupied(4))
    aluMonitor.laneIdx.ref := index.U
  }

  // slot occupied
  dut.laneVec.zipWithIndex.foreach { case (lane, index) =>
    val slotNum = lane.slotOccupied.length
    val chainingMonitor = Module(new ChainingMonitor(ChainingMonitorParameter(slotNum, 3)))
    chainingMonitor.clock.ref := clock.asBool
    chainingMonitor.laneIdx.ref := index.U
    chainingMonitor.slotOccupied.ref := tapAndRead(lane.slotOccupied).asUInt
  }
}
