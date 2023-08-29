// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package tests.elaborate

import chisel3._
import chisel3.experimental.ExtModule
import chisel3.util.HasExtModuleInline

import v.{V, VRFWriteRequest}

class Monitor(dut: V) extends TapModule {
  val clock = IO(Input(Clock()))
  val reset = IO(Input(Bool()))

  // lane vrf write
  dut.laneVec.zipWithIndex.foreach { case (lane, index) =>
    val vrfPeeker = Module(new ExtModule with HasExtModuleInline {
      override val desiredName = "dpiVRFMonitor"
      val clock = IO(Input(Clock()))
      val vrfWriteValid = IO(Input(Bool()))
      val laneIdx = IO(Input(UInt(32.W)))
      setInline(
        s"$desiredName.sv",
        s"""module $desiredName(
           |  input clock,
           |  input int laneIdx,
           |  input bit vrfWriteValid
           |);
           |import "DPI-C" function void $desiredName(
           |  input int laneIdx,
           |  input bit vrfWriteValid
           |);
           |always @ (posedge clock) #(3) $desiredName(laneIdx, vrfWriteValid);
           |endmodule
           |""".stripMargin
      )
    })
    vrfPeeker.laneIdx := index.U
    vrfPeeker.vrfWriteValid := tap(lane.vrf.write.valid)
    vrfPeeker.clock := clock
  }

  // alu occupied
  dut.laneVec.zipWithIndex.foreach { case (lane, index) =>
    val aluPeeker = Module(new ExtModule with HasExtModuleInline {
      override val desiredName = "dpiALUMonitor"
      val clock = IO(Input(Clock()))
      val laneIdx = IO(Input(UInt(32.W)))
      val isAdderOccupied = IO(Input(Bool()))
      val isShifterOccupied = IO(Input(Bool()))
      val isMultiplierOccupied = IO(Input(Bool()))
      val isDividerOccupied = IO(Input(Bool()))
      setInline(
        s"$desiredName.sv",
        s"""module $desiredName(
           |  input int laneIdx,
           |  input bit isAdderOccupied,
           |  input bit isShifterOccupied,
           |  input bit isMultiplierOccupied,
           |  input bit isDividerOccupied,
           |  input clock
           |);
           |import "DPI-C" function void $desiredName(
           |  input int laneIdx,
           |  input bit isAdderOccupied,
           |  input bit isShifterOccupied,
           |  input bit isMultiplierOccupied,
           |  input bit isDividerOccupied
           |);
           |always @ (posedge clock) #(3) $desiredName(laneIdx, isAdderOccupied, isShifterOccupied, isMultiplierOccupied, isDividerOccupied);
           |endmodule
           |""".stripMargin
      )
    })
    aluPeeker.isAdderOccupied := tap(lane.executeOccupied)(1)
    aluPeeker.isShifterOccupied := tap(lane.executeOccupied)(2)
    aluPeeker.isMultiplierOccupied := tap(lane.executeOccupied)(3)
    aluPeeker.isDividerOccupied := tap(lane.executeOccupied)(4)
    aluPeeker.laneIdx := index.U

    aluPeeker.clock := clock
  }

  // slot occupied
  dut.laneVec.zipWithIndex.foreach { case (lane, index) =>
    val slotNum = lane.slotOccupied.length
    val slotPeeker = Module(new ExtModule with HasExtModuleInline {
      override val desiredName = "dpiChainingMonitor"
      val clock = IO(Input(Clock()))
      val laneIdx = IO(Input(UInt(32.W)))
      val slotOccupied = IO(Input(UInt(slotNum.W)))
        setInline(
        s"$desiredName.sv",
        s"""module $desiredName(
           |  input clock,
           |  input int laneIdx,
           |  input bit[${slotNum - 1}:0] slotOccupied
           |);
           |import "DPI-C" function void $desiredName(
           |  input int laneIdx,
           |  input bit[${slotNum - 1}:0] slotOccupied
           |);
           |always @ (posedge clock) #(3) $desiredName(laneIdx, slotOccupied);
           |endmodule
           |""".stripMargin
      )
    })
    slotPeeker.laneIdx := index.U
    slotPeeker.slotOccupied := tap(lane.slotOccupied).asUInt
    slotPeeker.clock := clock
  }

  done()
}
