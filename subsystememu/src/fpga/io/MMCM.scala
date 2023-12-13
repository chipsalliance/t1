package verdes.fpga.io

import chisel3._
import freechips.rocketchip.util.ElaborationArtefacts

class MMCMBlackBox(val freq: Float) extends BlackBox {
  val io = IO(new Bundle{
    val clk_out1 = Output(Clock())
    val locked = Output(Bool())
    val clk_in1_p = Input(Bool())
    val clk_in1_n = Input(Bool())
  })
  ElaborationArtefacts.add(desiredName + ".tcl",
    s"""
       |create_ip -name clk_wiz -vendor xilinx.com -library ip -version 6.0 -module_name $desiredName
       |set_property -dict [list \\
       |  CONFIG.CLKIN1_JITTER_PS {33.330000000000005} \\
       |  CONFIG.CLKOUT1_REQUESTED_OUT_FREQ {$freq} \\
       |  CONFIG.CLK_IN1_BOARD_INTERFACE {default_sysclk1_300} \\
       |  CONFIG.MMCM_CLKFBOUT_MULT_F {4.000} \\
       |  CONFIG.MMCM_CLKIN1_PERIOD {3.333} \\
       |  CONFIG.PRIM_IN_FREQ {300.000} \\
       |  CONFIG.PRIM_SOURCE {Differential_clock_capable_pin} \\
       |  CONFIG.USE_RESET {false} \\
       |] [get_ips $desiredName]
       |""".stripMargin)
}

class MMCM(val freq: Float = 100) extends RawModule {
  val io = IO(new Bundle {
    val clk_in = Input(new DifferentialClock())
    val clk_out = Output(Clock())
    val out_reset = Output(Reset())
  })
  val inst = Module(new MMCMBlackBox(freq))
  inst.io.clk_in1_p := io.clk_in.p
  inst.io.clk_in1_n := io.clk_in.n
  io.clk_out := inst.io.clk_out1
  io.out_reset := !inst.io.locked
}
