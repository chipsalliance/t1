package verdes.fpga.io

import chisel3._
import chisel3.experimental._
import freechips.rocketchip.amba.axi4.{AXI4Bundle, AXI4BundleParameters}
import freechips.rocketchip.util.ElaborationArtefacts
class DDRMIGBlackBox(val dataWidth: Int, val idWidth: Int) extends BlackBox {
  val io = IO(new Bundle {
    val sys_rst = Input(Bool())
    val c0_sys_clk_p = Input(Bool())
    val c0_sys_clk_n = Input(Bool())
    val c0_ddr4_act_n = Output(Bool())
    val c0_ddr4_adr = Output(UInt(17.W))
    val c0_ddr4_ba = Output(UInt(2.W))
    val c0_ddr4_bg = Output(UInt(1.W))
    val c0_ddr4_cke = Output(UInt(1.W))
    val c0_ddr4_odt = Output(UInt(1.W))
    val c0_ddr4_cs_n = Output(UInt(1.W))
    val c0_ddr4_ck_t = Output(UInt(1.W))
    val c0_ddr4_ck_c = Output(UInt(1.W))
    val c0_ddr4_reset_n = Output(Bool())
    val c0_ddr4_dm_dbi_n = Analog(8.W)
    val c0_ddr4_dq = Analog(64.W)
    val c0_ddr4_dqs_c = Analog(8.W)
    val c0_ddr4_dqs_t = Analog(8.W)
    val c0_init_calib_complete = Output(Bool())
    val c0_ddr4_ui_clk = Output(Clock())
    val c0_ddr4_ui_clk_sync_rst = Output(Bool())
    val addn_ui_clkout1 = Output(Clock())
    val dbg_clk = Output(Clock())
    val c0_ddr4_aresetn = Input(Bool())
    val c0_ddr4_s_axi_awid = Input(UInt(idWidth.W))
    val c0_ddr4_s_axi_awaddr = Input(UInt(31.W))
    val c0_ddr4_s_axi_awlen = Input(UInt(8.W))
    val c0_ddr4_s_axi_awsize = Input(UInt(3.W))
    val c0_ddr4_s_axi_awburst = Input(UInt(2.W))
    val c0_ddr4_s_axi_awlock = Input(UInt(1.W))
    val c0_ddr4_s_axi_awcache = Input(UInt(4.W))
    val c0_ddr4_s_axi_awprot = Input(UInt(3.W))
    val c0_ddr4_s_axi_awqos = Input(UInt(4.W))
    val c0_ddr4_s_axi_awvalid = Input(Bool())
    val c0_ddr4_s_axi_awready = Output(Bool())
    val c0_ddr4_s_axi_wdata = Input(UInt(dataWidth.W))
    val c0_ddr4_s_axi_wstrb = Input(UInt((dataWidth/8).W))
    val c0_ddr4_s_axi_wlast = Input(Bool())
    val c0_ddr4_s_axi_wvalid = Input(Bool())
    val c0_ddr4_s_axi_wready = Output(Bool())
    val c0_ddr4_s_axi_bready = Input(Bool())
    val c0_ddr4_s_axi_bid = Output(UInt(idWidth.W))
    val c0_ddr4_s_axi_bresp = Output(UInt(2.W))
    val c0_ddr4_s_axi_bvalid = Output(Bool())
    val c0_ddr4_s_axi_arid = Input(UInt(idWidth.W))
    val c0_ddr4_s_axi_araddr = Input(UInt(31.W))
    val c0_ddr4_s_axi_arlen = Input(UInt(8.W))
    val c0_ddr4_s_axi_arsize = Input(UInt(3.W))
    val c0_ddr4_s_axi_arburst = Input(UInt(2.W))
    val c0_ddr4_s_axi_arlock = Input(UInt(1.W))
    val c0_ddr4_s_axi_arcache = Input(UInt(4.W))
    val c0_ddr4_s_axi_arprot = Input(UInt(3.W))
    val c0_ddr4_s_axi_arqos = Input(UInt(4.W))
    val c0_ddr4_s_axi_arvalid = Input(Bool())
    val c0_ddr4_s_axi_arready = Output(Bool())
    val c0_ddr4_s_axi_rready = Input(Bool())
    val c0_ddr4_s_axi_rid = Output(UInt(idWidth.W))
    val c0_ddr4_s_axi_rdata = Output(UInt(dataWidth.W))
    val c0_ddr4_s_axi_rresp = Output(UInt(2.W))
    val c0_ddr4_s_axi_rlast = Output(Bool())
    val c0_ddr4_s_axi_rvalid = Output(Bool())
    val dbg_bus = Output(UInt(512.W))
  })
  ElaborationArtefacts.add(desiredName + ".tcl",
    s"""
      |create_ip -name ddr4 -vendor xilinx.com -library ip -version 2.2 -module_name $desiredName
      |set_property -dict [list \\
      |  CONFIG.C0_CLOCK_BOARD_INTERFACE {default_250mhz_clk1} \\
      |  CONFIG.C0_DDR4_BOARD_INTERFACE {ddr4_sdram_c1_083} \\
      |  CONFIG.RESET_BOARD_INTERFACE {Custom} \\
      |  CONFIG.ADDN_UI_CLKOUT1_FREQ_HZ {100} \\
      |  CONFIG.C0.DDR4_AxiAddressWidth {31} \\
      |  CONFIG.C0.DDR4_AxiDataWidth {$dataWidth} \\
      |  CONFIG.C0.DDR4_AxiIDWidth {$idWidth} \\
      |  CONFIG.C0.DDR4_AxiSelection {true} \\
      |  CONFIG.C0.DDR4_AxiNarrowBurst {true} \\
      |  CONFIG.C0.DDR4_DataWidth {64} \\
      |  CONFIG.C0.DDR4_InputClockPeriod {4000} \\
      |  CONFIG.C0.DDR4_MemoryPart {MT40A256M16GE-083E} \\
      |  CONFIG.C0.DDR4_TimePeriod {833} \\
      |  CONFIG.Phy_Only {Complete_Memory_Controller} \\
      |] [get_ips $desiredName]
      |""".stripMargin)
}

class DDRMIG(val dataWidth: Int = 512, val idWidth: Int = 4) extends RawModule {
  val io = IO(new Bundle {
    val sys_clk = Input(new DifferentialClock())
    val sys_reset = Input(Reset())
    val axi_reset = Input(Reset())
    val axi_slave = Flipped(new AXI4Bundle(new AXI4BundleParameters(31, dataWidth, idWidth)))
    val axi_clock = Output(Clock())
    val axi_clock_rst = Output(Bool())
    val add_clkout1 = Output(Clock())
    val ddr = new VCU118DDR()
  })
  val ddr_inst = Module(new DDRMIGBlackBox(dataWidth, idWidth))

  def isInitCalibCompelete: Bool = ddr_inst.io.c0_init_calib_complete

  
  // Clock
  ddr_inst.io.c0_sys_clk_p := io.sys_clk.p
  ddr_inst.io.c0_sys_clk_n := io.sys_clk.n
  io.axi_clock := ddr_inst.io.c0_ddr4_ui_clk

  // add ui clock
  io.add_clkout1 := ddr_inst.io.addn_ui_clkout1

  // Reset
  ddr_inst.io.sys_rst := io.sys_reset
  ddr_inst.io.c0_ddr4_aresetn := !io.axi_reset.asBool
  io.axi_clock_rst := ddr_inst.io.c0_ddr4_ui_clk_sync_rst

  // DDR
  io.ddr.act_n := ddr_inst.io.c0_ddr4_act_n
  io.ddr.adr := ddr_inst.io.c0_ddr4_adr
  io.ddr.ba := ddr_inst.io.c0_ddr4_ba
  io.ddr.bg := ddr_inst.io.c0_ddr4_bg
  io.ddr.cke := ddr_inst.io.c0_ddr4_cke
  io.ddr.odt := ddr_inst.io.c0_ddr4_odt
  io.ddr.cs_n := ddr_inst.io.c0_ddr4_cs_n
  io.ddr.ck_t := ddr_inst.io.c0_ddr4_ck_t
  io.ddr.ck_c := ddr_inst.io.c0_ddr4_ck_c
  io.ddr.reset_n := ddr_inst.io.c0_ddr4_reset_n
  io.ddr.dm_dbi_n <> ddr_inst.io.c0_ddr4_dm_dbi_n
  io.ddr.dq <> ddr_inst.io.c0_ddr4_dq
  io.ddr.dqs_c <> ddr_inst.io.c0_ddr4_dqs_c
  io.ddr.dqs_t <> ddr_inst.io.c0_ddr4_dqs_t

  // AXI
  //   aw
  ddr_inst.io.c0_ddr4_s_axi_awid := io.axi_slave.aw.bits.id
  ddr_inst.io.c0_ddr4_s_axi_awaddr := io.axi_slave.aw.bits.addr
  ddr_inst.io.c0_ddr4_s_axi_awlen := io.axi_slave.aw.bits.len
  ddr_inst.io.c0_ddr4_s_axi_awsize := io.axi_slave.aw.bits.size
  ddr_inst.io.c0_ddr4_s_axi_awburst := io.axi_slave.aw.bits.burst
  ddr_inst.io.c0_ddr4_s_axi_awlock := io.axi_slave.aw.bits.lock
  ddr_inst.io.c0_ddr4_s_axi_awcache := io.axi_slave.aw.bits.cache
  ddr_inst.io.c0_ddr4_s_axi_awprot := io.axi_slave.aw.bits.prot
  ddr_inst.io.c0_ddr4_s_axi_awqos := io.axi_slave.aw.bits.qos
  ddr_inst.io.c0_ddr4_s_axi_awvalid := io.axi_slave.aw.valid
  io.axi_slave.aw.ready := ddr_inst.io.c0_ddr4_s_axi_awready
  //   w
  ddr_inst.io.c0_ddr4_s_axi_wdata := io.axi_slave.w.bits.data
  ddr_inst.io.c0_ddr4_s_axi_wstrb := io.axi_slave.w.bits.strb
  ddr_inst.io.c0_ddr4_s_axi_wlast := io.axi_slave.w.bits.last
  ddr_inst.io.c0_ddr4_s_axi_wvalid := io.axi_slave.w.valid
  io.axi_slave.w.ready := ddr_inst.io.c0_ddr4_s_axi_wready
  //   ar
  ddr_inst.io.c0_ddr4_s_axi_arid := io.axi_slave.ar.bits.id
  ddr_inst.io.c0_ddr4_s_axi_araddr := io.axi_slave.ar.bits.addr
  ddr_inst.io.c0_ddr4_s_axi_arlen := io.axi_slave.ar.bits.len
  ddr_inst.io.c0_ddr4_s_axi_arsize := io.axi_slave.ar.bits.size
  ddr_inst.io.c0_ddr4_s_axi_arburst := io.axi_slave.ar.bits.burst
  ddr_inst.io.c0_ddr4_s_axi_arlock := io.axi_slave.ar.bits.lock
  ddr_inst.io.c0_ddr4_s_axi_arcache := io.axi_slave.ar.bits.cache
  ddr_inst.io.c0_ddr4_s_axi_arprot := io.axi_slave.ar.bits.prot
  ddr_inst.io.c0_ddr4_s_axi_arqos := io.axi_slave.ar.bits.qos
  ddr_inst.io.c0_ddr4_s_axi_arvalid := io.axi_slave.ar.valid
  io.axi_slave.ar.ready := ddr_inst.io.c0_ddr4_s_axi_arready
  //   r
  io.axi_slave.r.bits.id := ddr_inst.io.c0_ddr4_s_axi_rid
  io.axi_slave.r.bits.data := ddr_inst.io.c0_ddr4_s_axi_rdata
  io.axi_slave.r.bits.resp := ddr_inst.io.c0_ddr4_s_axi_rresp
  io.axi_slave.r.bits.last := ddr_inst.io.c0_ddr4_s_axi_rlast
  io.axi_slave.r.valid := ddr_inst.io.c0_ddr4_s_axi_rvalid
  ddr_inst.io.c0_ddr4_s_axi_rready := io.axi_slave.r.ready
  //    b
  io.axi_slave.b.bits.id := ddr_inst.io.c0_ddr4_s_axi_bid
  io.axi_slave.b.bits.resp := ddr_inst.io.c0_ddr4_s_axi_bresp
  io.axi_slave.b.valid := ddr_inst.io.c0_ddr4_s_axi_bvalid
  ddr_inst.io.c0_ddr4_s_axi_bready := io.axi_slave.b.ready
}