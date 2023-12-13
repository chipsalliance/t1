package verdes.fpga.io

import chisel3._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.util.ElaborationArtefacts


class XDMABlackBoxIOBundleBase(dataWidth: Int, PCIeLane: Int) extends Bundle {
  val sys_clk = Input(Clock())
  val sys_clk_gt = Input(Clock())
  val sys_rst_n = Input(Bool())
  val user_lnk_up = Output(Bool())
  val pci_exp_txp = Output(UInt(PCIeLane.W))
  val pci_exp_txn = Output(UInt(PCIeLane.W))
  val pci_exp_rxp = Input(UInt(PCIeLane.W))
  val pci_exp_rxn = Input(UInt(PCIeLane.W))
  val axi_aclk = Output(Clock())
  val axi_aresetn = Output(Bool())
  val usr_irq_req = Input(UInt(1.W))
  val usr_irq_ack = Output(UInt(1.W))
  val msi_enable = Output(Bool())
  val msi_vector_width = Output(UInt(3.W))
  val m_axi_awready = Input(Bool())
  val m_axi_wready = Input(Bool())
  val m_axi_bid = Input(UInt(4.W))
  val m_axi_bresp = Input(UInt(2.W))
  val m_axi_bvalid = Input(Bool())
  val m_axi_arready = Input(Bool())
  val m_axi_rid = Input(UInt(4.W))
  val m_axi_rdata = Input(UInt(dataWidth.W))
  val m_axi_rresp = Input(UInt(2.W))
  val m_axi_rlast = Input(Bool())
  val m_axi_rvalid = Input(Bool())
  val m_axi_awid = Output(UInt(4.W))
  val m_axi_awaddr = Output(UInt(64.W))
  val m_axi_awlen = Output(UInt(8.W))
  val m_axi_awsize = Output(UInt(3.W))
  val m_axi_awburst = Output(UInt(2.W))
  val m_axi_awprot = Output(UInt(3.W))
  val m_axi_awvalid = Output(Bool())
  val m_axi_awlock = Output(Bool())
  val m_axi_awcache = Output(UInt(4.W))
  val m_axi_wdata = Output(UInt(dataWidth.W))
  val m_axi_wstrb = Output(UInt((dataWidth / 8).W))
  val m_axi_wlast = Output(Bool())
  val m_axi_wvalid = Output(Bool())
  val m_axi_bready = Output(Bool())
  val m_axi_arid = Output(UInt(4.W))
  val m_axi_araddr = Output(UInt(64.W))
  val m_axi_arlen = Output(UInt(8.W))
  val m_axi_arsize = Output(UInt(3.W))
  val m_axi_arburst = Output(UInt(2.W))
  val m_axi_arprot = Output(UInt(3.W))
  val m_axi_arvalid = Output(Bool())
  val m_axi_arlock = Output(Bool())
  val m_axi_arcache = Output(UInt(4.W))
  val m_axi_rready = Output(Bool())
}

class XDMABlackBoxIOBundleWithAXIL(val dataWidth: Int = 512, val PCIeLane: Int = 16)
  extends XDMABlackBoxIOBundleBase(dataWidth, PCIeLane) {
  val m_axil_awaddr = Output(UInt(32.W))
  val m_axil_awprot = Output(UInt(3.W))
  val m_axil_awvalid = Output(Bool())
  val m_axil_awready = Input(Bool())
  val m_axil_wdata = Output(UInt(32.W))
  val m_axil_wstrb = Output(UInt(4.W))
  val m_axil_wvalid = Output(Bool())
  val m_axil_wready = Input(Bool())
  val m_axil_bvalid = Input(Bool())
  val m_axil_bresp = Input(UInt(2.W))
  val m_axil_bready = Output(Bool())
  val m_axil_araddr = Output(UInt(32.W))
  val m_axil_arprot = Output(UInt(3.W))
  val m_axil_arvalid = Output(Bool())
  val m_axil_arready = Input(Bool())
  val m_axil_rdata = Input(UInt(32.W))
  val m_axil_rresp = Input(UInt(2.W))
  val m_axil_rvalid = Input(Bool())
  val m_axil_rready = Output(Bool())
}
class XDMABlackBox(val dataWidth: Int, val PCIeLane: Int, val hasAXIL: Boolean) extends BlackBox {
  val io = IO(if (hasAXIL) new XDMABlackBoxIOBundleWithAXIL(dataWidth, PCIeLane)
                      else new XDMABlackBoxIOBundleBase(dataWidth, PCIeLane))
  ElaborationArtefacts.add(desiredName + ".tcl",
    s"""
       |create_ip -name xdma -vendor xilinx.com -library ip -version 4.1 -module_name $desiredName
       |set_property -dict [list \\
       |  CONFIG.PCIE_BOARD_INTERFACE {pci_express_x16} \\
       |  CONFIG.SYS_RST_N_BOARD_INTERFACE {pcie_perstn} \\
       |  CONFIG.axil_master_64bit_en {true} \\
       |  CONFIG.axil_master_prefetchable {true} \\
       |  CONFIG.axilite_master_en {true} \\
       |  CONFIG.axilite_master_scale {Gigabytes} \\
       |  CONFIG.axilite_master_size {4} \\
       |  CONFIG.cfg_mgmt_if {false} \\
       |  CONFIG.en_gt_selection {true} \\
       |  CONFIG.mode_selection {Advanced} \\
       |  CONFIG.pl_link_cap_max_link_speed {8.0_GT/s} \\
       |  CONFIG.pl_link_cap_max_link_width {X16} \\
       |] [get_ips $desiredName]
       |""".stripMargin)
}
class XDMA(val dataWidth: Int = 512, val PCIeLane: Int = 16, val hasAXIL: Boolean = false) extends RawModule {
  val io = IO(new Bundle {
    val pcie_presetn = Input(Bool())
    val pcie_clk = Input(new DifferentialClock())
    val pcie = new PCIeBundle(new PCIeParameters(16))
    val axi_mm_clk = Output(Clock())
    val axi_mm_rst = Output(Reset())
    val axi_mm = new AXI4Bundle(new AXI4BundleParameters(64, 512, 4))
    val axil_mmio = if (hasAXIL)
      Some(new AXILiteBundle(AXILiteBundleParameters(32, 32, hasProt = true))) else None
    val irq_req = Input(Bool())
    val irq_ack = Output(Bool())
  })
  val clock_buffer = Module(new IBUFDSGTE_PCIe)
  val xdma_inst = Module(new XDMABlackBox(dataWidth, PCIeLane, hasAXIL))

  // clock
  clock_buffer.io.IBUF_DS_P := io.pcie_clk.p
  clock_buffer.io.IBUF_DS_N := io.pcie_clk.n
  xdma_inst.io.sys_clk_gt := clock_buffer.io.IBUF_OUT
  xdma_inst.io.sys_clk := clock_buffer.io.IBUF_DS_ODIV2

  // reset
  xdma_inst.io.sys_rst_n := io.pcie_presetn

  // status
  def isLinkUp: Bool = xdma_inst.io.user_lnk_up


  // irq
  def isMSIEnable: Bool = xdma_inst.io.msi_enable.asBool
  def getMSIVectorSize: UInt = xdma_inst.io.msi_vector_width
  xdma_inst.io.usr_irq_req := io.irq_req
  io.irq_ack := xdma_inst.io.usr_irq_ack


  // pcie
  io.pcie.txp := xdma_inst.io.pci_exp_txp
  io.pcie.txn := xdma_inst.io.pci_exp_txn
  xdma_inst.io.pci_exp_rxp := io.pcie.rxp
  xdma_inst.io.pci_exp_rxn := io.pcie.rxn

  // maxi
  // clock and reset
  io.axi_mm_clk := xdma_inst.io.axi_aclk
  io.axi_mm_rst := !xdma_inst.io.axi_aresetn.asBool

  //   ar
  io.axi_mm.ar.bits.id        := xdma_inst.io.m_axi_arid
  io.axi_mm.ar.bits.addr      := xdma_inst.io.m_axi_araddr
  io.axi_mm.ar.bits.len       := xdma_inst.io.m_axi_arlen
  io.axi_mm.ar.bits.size      := xdma_inst.io.m_axi_arsize
  io.axi_mm.ar.bits.burst     := xdma_inst.io.m_axi_arburst
  io.axi_mm.ar.bits.prot      := xdma_inst.io.m_axi_arprot
  io.axi_mm.ar.bits.cache     := xdma_inst.io.m_axi_arcache
  io.axi_mm.ar.bits.qos       := 0.U
  io.axi_mm.ar.bits.lock      := xdma_inst.io.m_axi_arlock
  io.axi_mm.ar.valid          := xdma_inst.io.m_axi_arvalid
  xdma_inst.io.m_axi_arready  := io.axi_mm.ar.ready
  //   r
  xdma_inst.io.m_axi_rid      := io.axi_mm.r.bits.id
  xdma_inst.io.m_axi_rdata    := io.axi_mm.r.bits.data
  xdma_inst.io.m_axi_rresp    := io.axi_mm.r.bits.resp
  xdma_inst.io.m_axi_rlast    := io.axi_mm.r.bits.last
  xdma_inst.io.m_axi_rvalid   := io.axi_mm.r.valid
  io.axi_mm.r.ready           := xdma_inst.io.m_axi_rready
  //   aw
  io.axi_mm.aw.bits.id        := xdma_inst.io.m_axi_awid
  io.axi_mm.aw.bits.addr      := xdma_inst.io.m_axi_awaddr
  io.axi_mm.aw.bits.len       := xdma_inst.io.m_axi_awlen
  io.axi_mm.aw.bits.size      := xdma_inst.io.m_axi_awsize
  io.axi_mm.aw.bits.burst     := xdma_inst.io.m_axi_awburst
  io.axi_mm.aw.bits.prot      := xdma_inst.io.m_axi_awprot
  io.axi_mm.aw.bits.cache     := xdma_inst.io.m_axi_awcache
  io.axi_mm.aw.bits.qos       := 0.U
  io.axi_mm.aw.bits.lock      := xdma_inst.io.m_axi_awlock
  io.axi_mm.aw.valid          := xdma_inst.io.m_axi_awvalid
  xdma_inst.io.m_axi_awready  := io.axi_mm.aw.ready
  //   w
  io.axi_mm.w.bits.data       := xdma_inst.io.m_axi_wdata
  io.axi_mm.w.bits.strb       := xdma_inst.io.m_axi_wstrb
  io.axi_mm.w.bits.last       := xdma_inst.io.m_axi_wlast
  io.axi_mm.w.valid           := xdma_inst.io.m_axi_wvalid
  xdma_inst.io.m_axi_wready   := io.axi_mm.w.ready
  //   b
  xdma_inst.io.m_axi_bid      := io.axi_mm.b.bits.id
  xdma_inst.io.m_axi_bresp    := io.axi_mm.b.bits.resp
  xdma_inst.io.m_axi_bvalid   := io.axi_mm.b.valid
  io.axi_mm.b.ready           := xdma_inst.io.m_axi_bready

  // maxil
  xdma_inst.io match {
    case l: XDMABlackBoxIOBundleWithAXIL => {
      io.axil_mmio.get.aw.bits.addr := l.m_axil_awaddr
      io.axil_mmio.get.aw.bits.prot := l.m_axil_awprot
      io.axil_mmio.get.aw.valid := l.m_axil_awvalid
      l.m_axil_awready := io.axil_mmio.get.aw.ready
      io.axil_mmio.get.w.bits.data := l.m_axil_wdata
      io.axil_mmio.get.w.bits.strb := l.m_axil_wstrb
      io.axil_mmio.get.w.valid := l.m_axil_wvalid
      l.m_axil_wready := io.axil_mmio.get.w.ready
      l.m_axil_bvalid := io.axil_mmio.get.b.valid
      l.m_axil_bresp := io.axil_mmio.get.b.bits.resp
      io.axil_mmio.get.b.ready := l.m_axil_bready
      io.axil_mmio.get.ar.bits.addr := l.m_axil_araddr
      io.axil_mmio.get.ar.bits.prot := l.m_axil_arprot
      io.axil_mmio.get.ar.valid := l.m_axil_arvalid
      l.m_axil_arready := io.axil_mmio.get.ar.ready
      l.m_axil_rdata := io.axil_mmio.get.r.bits.data
      l.m_axil_rresp := io.axil_mmio.get.r.bits.resp
      l.m_axil_rvalid := io.axil_mmio.get.r.valid
      io.axil_mmio.get.r.ready := l.m_axil_rready
    }
    case _ => {}
  }

}
