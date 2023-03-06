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
    val peeker = Module(new ExtModule with HasExtModuleInline {
      override val desiredName = "dpiVRFMonitor"
      val clock = IO(Input(Clock()))
      val valid = IO(Input(Bool()))
      val laneIdx = IO(Input(UInt(32.W)))
      setInline(
        s"$desiredName.sv",
        s"""module $desiredName(
           |  input clock,
           |  input int laneIdx,
           |  input bit valid
           |);
           |import "DPI-C" function void $desiredName(
           |  input int laneIdx,
           |  input bit valid
           |);
           |always @ (posedge clock) #(3) $desiredName(laneIdx, valid);
           |endmodule
           |""".stripMargin
      )
    })
    peeker.laneIdx := index.U
    peeker.valid := tap(lane.vrf.write.valid)
    peeker.clock := clock
  }

  // alu occupied
  dut.laneVec.zipWithIndex.foreach { case (lane, index) =>
    val peeker = Module(new ExtModule with HasExtModuleInline {
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
    peeker.isAdderOccupied := tap(lane.adderRequests).map(_.asUInt).reduce(_ | _) =/= 0.U
    peeker.isShifterOccupied := tap(lane.shiftRequests).map(_.asUInt).reduce(_ | _) =/= 0.U
    peeker.isMultiplierOccupied := tap(lane.multiplerRequests).map(_.asUInt).reduce(_ | _) =/= 0.U
    peeker.isDividerOccupied := tap(lane.dividerRequests).map(_.asUInt).reduce(_ | _) =/= 0.U
    peeker.laneIdx := index.U

    peeker.clock := clock
  }

  // lane vrf write
  dut.laneVec.zipWithIndex.foreach { case (lane, index) =>
    val slotNum = lane.slotOccupied.length
    val peeker = Module(new ExtModule with HasExtModuleInline {
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
    peeker.laneIdx := index.U
    peeker.slotOccupied := tap(lane.slotOccupied).asUInt
    peeker.clock := clock
  }

  done()
}
