package verdes.fpga.io

import chisel3._
import freechips.rocketchip.amba.axi4.{AXI4Bundle, AXI4BundleParameters}
import freechips.rocketchip.util.ElaborationArtefacts
class AXIClockConverterBlackBox(val addrWidth: Int, val dataWidth: Int, val idWidth: Int) extends BlackBox {
  override def desiredName: String = super.desiredName + addrWidth + "_" + dataWidth + "_" + idWidth
  val io = IO(new Bundle {
    val s_axi_aclk = Input(Clock())
    val s_axi_aresetn = Input(Bool())
    val s_axi_awid = Input(UInt(4.W))
    val s_axi_awaddr = Input(UInt(addrWidth.W))
    val s_axi_awlen = Input(UInt(8.W))
    val s_axi_awsize = Input(UInt(3.W))
    val s_axi_awburst = Input(UInt(2.W))
    val s_axi_awlock = Input(UInt(1.W))
    val s_axi_awcache = Input(UInt(4.W))
    val s_axi_awprot = Input(UInt(3.W))
    val s_axi_awregion = Input(UInt(4.W))
    val s_axi_awqos = Input(UInt(4.W))
    val s_axi_awvalid = Input(Bool())
    val s_axi_awready = Output(Bool())
    val s_axi_wdata = Input(UInt(dataWidth.W))
    val s_axi_wstrb = Input(UInt((dataWidth/8).W))
    val s_axi_wlast = Input(Bool())
    val s_axi_wvalid = Input(Bool())
    val s_axi_wready = Output(Bool())
    val s_axi_bid = Output(UInt(idWidth.W))
    val s_axi_bresp = Output(UInt(2.W))
    val s_axi_bvalid = Output(Bool())
    val s_axi_bready = Input(Bool())
    val s_axi_arid = Input(UInt(idWidth.W))
    val s_axi_araddr = Input(UInt(addrWidth.W))
    val s_axi_arlen = Input(UInt(8.W))
    val s_axi_arsize = Input(UInt(3.W))
    val s_axi_arburst = Input(UInt(2.W))
    val s_axi_arlock = Input(UInt(1.W))
    val s_axi_arcache = Input(UInt(4.W))
    val s_axi_arprot = Input(UInt(3.W))
    val s_axi_arregion = Input(UInt(4.W))
    val s_axi_arqos = Input(UInt(4.W))
    val s_axi_arvalid = Input(Bool())
    val s_axi_arready = Output(Bool())
    val s_axi_rid = Output(UInt(idWidth.W))
    val s_axi_rdata = Output(UInt(dataWidth.W))
    val s_axi_rresp = Output(UInt(2.W))
    val s_axi_rlast = Output(Bool())
    val s_axi_rvalid = Output(Bool())
    val s_axi_rready = Input(Bool())
    val m_axi_aclk = Input(Clock())
    val m_axi_aresetn = Input(Bool())
    val m_axi_awid = Output(UInt(idWidth.W))
    val m_axi_awaddr = Output(UInt(addrWidth.W))
    val m_axi_awlen = Output(UInt(8.W))
    val m_axi_awsize = Output(UInt(3.W))
    val m_axi_awburst = Output(UInt(2.W))
    val m_axi_awlock = Output(UInt(1.W))
    val m_axi_awcache = Output(UInt(4.W))
    val m_axi_awprot = Output(UInt(3.W))
    val m_axi_awregion = Output(UInt(4.W))
    val m_axi_awqos = Output(UInt(4.W))
    val m_axi_awvalid = Output(Bool())
    val m_axi_awready = Input(Bool())
    val m_axi_wdata = Output(UInt(dataWidth.W))
    val m_axi_wstrb = Output(UInt((dataWidth/8).W))
    val m_axi_wlast = Output(Bool())
    val m_axi_wvalid = Output(Bool())
    val m_axi_wready = Input(Bool())
    val m_axi_bid = Input(UInt(idWidth.W))
    val m_axi_bresp = Input(UInt(2.W))
    val m_axi_bvalid = Input(Bool())
    val m_axi_bready = Output(Bool())
    val m_axi_arid = Output(UInt(idWidth.W))
    val m_axi_araddr = Output(UInt(addrWidth.W))
    val m_axi_arlen = Output(UInt(8.W))
    val m_axi_arsize = Output(UInt(3.W))
    val m_axi_arburst = Output(UInt(2.W))
    val m_axi_arlock = Output(UInt(1.W))
    val m_axi_arcache = Output(UInt(4.W))
    val m_axi_arprot = Output(UInt(3.W))
    val m_axi_arregion = Output(UInt(4.W))
    val m_axi_arqos = Output(UInt(4.W))
    val m_axi_arvalid = Output(Bool())
    val m_axi_arready = Input(Bool())
    val m_axi_rid = Input(UInt(idWidth.W))
    val m_axi_rdata = Input(UInt(dataWidth.W))
    val m_axi_rresp = Input(UInt(2.W))
    val m_axi_rlast = Input(Bool())
    val m_axi_rvalid = Input(Bool())
    val m_axi_rready = Output(Bool())
  })
  ElaborationArtefacts.add(desiredName + ".tcl",
    s"""
     |create_ip -name axi_clock_converter -vendor xilinx.com -library ip -version 2.1 -module_name $desiredName
     |set_property -dict [list \\
     |  CONFIG.ADDR_WIDTH {$addrWidth} \\
     |  CONFIG.DATA_WIDTH {$dataWidth} \\
     |  CONFIG.ID_WIDTH {$idWidth} \\
     |] [get_ips $desiredName]
     |""".stripMargin)
}

class AXILiteClockConverterBlackBox(val addrWidth: Int, val dataWidth: Int) extends BlackBox {
  override def desiredName: String = super.desiredName + addrWidth + "_" + dataWidth
  val io = IO(new Bundle {
    val s_axi_aclk = Input(Clock())
    val s_axi_aresetn = Input(Bool())
    val s_axi_awaddr = Input(UInt(addrWidth.W))
    val s_axi_awprot = Input(UInt(3.W))
    val s_axi_awvalid = Input(Bool())
    val s_axi_awready = Output(Bool())
    val s_axi_wdata = Input(UInt(dataWidth.W))
    val s_axi_wstrb = Input(UInt((dataWidth/8).W))
    val s_axi_wvalid = Input(Bool())
    val s_axi_wready = Output(Bool())
    val s_axi_bresp = Output(UInt(2.W))
    val s_axi_bvalid = Output(Bool())
    val s_axi_bready = Input(Bool())
    val s_axi_araddr = Input(UInt(addrWidth.W))
    val s_axi_arprot = Input(UInt(3.W))
    val s_axi_arvalid = Input(Bool())
    val s_axi_arready = Output(Bool())
    val s_axi_rdata = Output(UInt(dataWidth.W))
    val s_axi_rresp = Output(UInt(2.W))
    val s_axi_rvalid = Output(Bool())
    val s_axi_rready = Input(Bool())
    val m_axi_aclk = Input(Clock())
    val m_axi_aresetn = Input(Bool())
    val m_axi_awaddr = Output(UInt(addrWidth.W))
    val m_axi_awprot = Output(UInt(3.W))
    val m_axi_awvalid = Output(Bool())
    val m_axi_awready = Input(Bool())
    val m_axi_wdata = Output(UInt(dataWidth.W))
    val m_axi_wstrb = Output(UInt((dataWidth/8).W))
    val m_axi_wvalid = Output(Bool())
    val m_axi_wready = Input(Bool())
    val m_axi_bresp = Input(UInt(2.W))
    val m_axi_bvalid = Input(Bool())
    val m_axi_bready = Output(Bool())
    val m_axi_araddr = Output(UInt(addrWidth.W))
    val m_axi_arprot = Output(UInt(3.W))
    val m_axi_arvalid = Output(Bool())
    val m_axi_arready = Input(Bool())
    val m_axi_rdata = Input(UInt(dataWidth.W))
    val m_axi_rresp = Input(UInt(2.W))
    val m_axi_rvalid = Input(Bool())
    val m_axi_rready = Output(Bool())
  })
  ElaborationArtefacts.add(desiredName + ".tcl",
    s"""
       |create_ip -name axi_clock_converter -vendor xilinx.com -library ip -version 2.1 -module_name $desiredName
       |set_property -dict [list \\
       |  CONFIG.ADDR_WIDTH {$addrWidth} \\
       |  CONFIG.DATA_WIDTH {$dataWidth} \\
       |  CONFIG.PROTOCOL {AXI4LITE} \\
       |] [get_ips $desiredName]
       |""".stripMargin)
}
class AXIClockConverter(val addrWidth: Int = 64, val dataWidth: Int = 512, val idWidth: Int = 4) extends RawModule {
  val io = IO(new Bundle {
    val s_axi_clock = Input(Clock())
    val s_axi_reset = Input(Bool())
    val s_axi = Flipped(new AXI4Bundle(new AXI4BundleParameters(addrWidth, dataWidth, idWidth)))
    val m_axi_clock = Input(Clock())
    val m_axi_reset = Input(Bool())
    val m_axi = new AXI4Bundle(new AXI4BundleParameters(addrWidth, dataWidth, idWidth))
  })
  val axi_cc_inst = Module(new AXIClockConverterBlackBox(addrWidth, dataWidth, idWidth))

  axi_cc_inst.io.s_axi_aclk := io.s_axi_clock
  axi_cc_inst.io.s_axi_aresetn := !io.s_axi_reset.asBool
  axi_cc_inst.io.s_axi_awid := io.s_axi.aw.bits.id
  axi_cc_inst.io.s_axi_awaddr := io.s_axi.aw.bits.addr
  axi_cc_inst.io.s_axi_awlen := io.s_axi.aw.bits.len
  axi_cc_inst.io.s_axi_awsize := io.s_axi.aw.bits.size
  axi_cc_inst.io.s_axi_awburst := io.s_axi.aw.bits.burst
  axi_cc_inst.io.s_axi_awlock := io.s_axi.aw.bits.lock
  axi_cc_inst.io.s_axi_awcache := io.s_axi.aw.bits.cache
  axi_cc_inst.io.s_axi_awprot := io.s_axi.aw.bits.prot
  axi_cc_inst.io.s_axi_awregion := 0.U
  axi_cc_inst.io.s_axi_awqos := io.s_axi.aw.bits.qos
  axi_cc_inst.io.s_axi_awvalid := io.s_axi.aw.valid
  io.s_axi.aw.ready := axi_cc_inst.io.s_axi_awready
  axi_cc_inst.io.s_axi_wdata := io.s_axi.w.bits.data
  axi_cc_inst.io.s_axi_wstrb := io.s_axi.w.bits.strb
  axi_cc_inst.io.s_axi_wlast := io.s_axi.w.bits.last
  axi_cc_inst.io.s_axi_wvalid := io.s_axi.w.valid
  io.s_axi.w.ready := axi_cc_inst.io.s_axi_wready
  io.s_axi.b.bits.id := axi_cc_inst.io.s_axi_bid
  io.s_axi.b.bits.resp := axi_cc_inst.io.s_axi_bresp
  io.s_axi.b.valid := axi_cc_inst.io.s_axi_bvalid
  axi_cc_inst.io.s_axi_bready := io.s_axi.b.ready
  axi_cc_inst.io.s_axi_arid := io.s_axi.ar.bits.id
  axi_cc_inst.io.s_axi_araddr := io.s_axi.ar.bits.addr
  axi_cc_inst.io.s_axi_arlen := io.s_axi.ar.bits.len
  axi_cc_inst.io.s_axi_arsize := io.s_axi.ar.bits.size
  axi_cc_inst.io.s_axi_arburst := io.s_axi.ar.bits.burst
  axi_cc_inst.io.s_axi_arlock := io.s_axi.ar.bits.lock
  axi_cc_inst.io.s_axi_arcache := io.s_axi.ar.bits.cache
  axi_cc_inst.io.s_axi_arprot := io.s_axi.ar.bits.prot
  axi_cc_inst.io.s_axi_arregion := 0.U
  axi_cc_inst.io.s_axi_arqos := io.s_axi.ar.bits.qos
  axi_cc_inst.io.s_axi_arvalid := io.s_axi.ar.valid
  io.s_axi.ar.ready := axi_cc_inst.io.s_axi_arready
  io.s_axi.r.bits.id := axi_cc_inst.io.s_axi_rid
  io.s_axi.r.bits.data := axi_cc_inst.io.s_axi_rdata
  io.s_axi.r.bits.resp := axi_cc_inst.io.s_axi_rresp
  io.s_axi.r.bits.last := axi_cc_inst.io.s_axi_rlast
  io.s_axi.r.valid := axi_cc_inst.io.s_axi_rvalid
  axi_cc_inst.io.s_axi_rready := io.s_axi.r.ready
  axi_cc_inst.io.m_axi_aclk := io.m_axi_clock
  axi_cc_inst.io.m_axi_aresetn := !io.m_axi_reset.asBool
  io.m_axi.aw.bits.id := axi_cc_inst.io.m_axi_awid
  io.m_axi.aw.bits.addr := axi_cc_inst.io.m_axi_awaddr
  io.m_axi.aw.bits.len := axi_cc_inst.io.m_axi_awlen
  io.m_axi.aw.bits.size := axi_cc_inst.io.m_axi_awsize
  io.m_axi.aw.bits.burst := axi_cc_inst.io.m_axi_awburst
  io.m_axi.aw.bits.lock := axi_cc_inst.io.m_axi_awlock
  io.m_axi.aw.bits.cache := axi_cc_inst.io.m_axi_awcache
  io.m_axi.aw.bits.prot := axi_cc_inst.io.m_axi_awprot
  io.m_axi.aw.bits.qos := axi_cc_inst.io.m_axi_awqos
  io.m_axi.aw.valid := axi_cc_inst.io.m_axi_awvalid
  axi_cc_inst.io.m_axi_awready := io.m_axi.aw.ready
  io.m_axi.w.bits.data := axi_cc_inst.io.m_axi_wdata
  io.m_axi.w.bits.strb := axi_cc_inst.io.m_axi_wstrb
  io.m_axi.w.bits.last := axi_cc_inst.io.m_axi_wlast
  io.m_axi.w.valid := axi_cc_inst.io.m_axi_wvalid
  axi_cc_inst.io.m_axi_wready := io.m_axi.w.ready
  axi_cc_inst.io.m_axi_bid := io.m_axi.b.bits.id
  axi_cc_inst.io.m_axi_bresp := io.m_axi.b.bits.resp
  axi_cc_inst.io.m_axi_bvalid := io.m_axi.b.valid
  io.m_axi.b.ready := axi_cc_inst.io.m_axi_bready
  io.m_axi.ar.bits.id := axi_cc_inst.io.m_axi_arid
  io.m_axi.ar.bits.addr := axi_cc_inst.io.m_axi_araddr
  io.m_axi.ar.bits.len := axi_cc_inst.io.m_axi_arlen
  io.m_axi.ar.bits.size := axi_cc_inst.io.m_axi_arsize
  io.m_axi.ar.bits.burst := axi_cc_inst.io.m_axi_arburst
  io.m_axi.ar.bits.lock := axi_cc_inst.io.m_axi_arlock
  io.m_axi.ar.bits.cache := axi_cc_inst.io.m_axi_arcache
  io.m_axi.ar.bits.prot := axi_cc_inst.io.m_axi_arprot
  io.m_axi.ar.bits.qos := axi_cc_inst.io.m_axi_arqos
  io.m_axi.ar.valid := axi_cc_inst.io.m_axi_arvalid
  axi_cc_inst.io.m_axi_arready := io.m_axi.ar.ready
  axi_cc_inst.io.m_axi_rid := io.m_axi.r.bits.id
  axi_cc_inst.io.m_axi_rdata := io.m_axi.r.bits.data
  axi_cc_inst.io.m_axi_rresp := io.m_axi.r.bits.resp
  axi_cc_inst.io.m_axi_rlast := io.m_axi.r.bits.last
  axi_cc_inst.io.m_axi_rvalid := io.m_axi.r.valid
  io.m_axi.r.ready := axi_cc_inst.io.m_axi_rready
}

class AXILiteClockConverter(val addrWidth: Int = 32, val dataWidth: Int = 32) extends RawModule {
  val io = IO(new Bundle {
    val s_axil_clock = Input(Clock())
    val s_axil_reset = Input(Bool())
    val s_axil = Flipped(new AXILiteBundle(AXILiteBundleParameters(addrWidth, dataWidth, true)))
    val m_axil_clock = Input(Clock())
    val m_axil_reset = Input(Bool())
    val m_axil = new AXILiteBundle(AXILiteBundleParameters(addrWidth, dataWidth, true))
  })
  val axil_cc_inst = Module(new AXILiteClockConverterBlackBox(addrWidth, dataWidth))

  axil_cc_inst.io.s_axi_aclk := io.s_axil_clock
  axil_cc_inst.io.s_axi_aresetn := !io.s_axil_reset.asBool
  axil_cc_inst.io.s_axi_awaddr := io.s_axil.aw.bits.addr
  axil_cc_inst.io.s_axi_awprot := io.s_axil.aw.bits.prot
  axil_cc_inst.io.s_axi_awvalid := io.s_axil.aw.valid
  io.s_axil.aw.ready := axil_cc_inst.io.s_axi_awready
  axil_cc_inst.io.s_axi_wdata := io.s_axil.w.bits.data
  axil_cc_inst.io.s_axi_wstrb := io.s_axil.w.bits.strb
  axil_cc_inst.io.s_axi_wvalid := io.s_axil.w.valid
  io.s_axil.w.ready := axil_cc_inst.io.s_axi_wready
  io.s_axil.b.bits.resp := axil_cc_inst.io.s_axi_bresp
  io.s_axil.b.valid := axil_cc_inst.io.s_axi_bvalid
  axil_cc_inst.io.s_axi_bready := io.s_axil.b.ready
  axil_cc_inst.io.s_axi_araddr := io.s_axil.ar.bits.addr
  axil_cc_inst.io.s_axi_arprot := io.s_axil.ar.bits.prot
  axil_cc_inst.io.s_axi_arvalid := io.s_axil.ar.valid
  io.s_axil.ar.ready := axil_cc_inst.io.s_axi_arready
  io.s_axil.r.bits.data := axil_cc_inst.io.s_axi_rdata
  io.s_axil.r.bits.resp := axil_cc_inst.io.s_axi_rresp
  io.s_axil.r.valid := axil_cc_inst.io.s_axi_rvalid
  axil_cc_inst.io.s_axi_rready := io.s_axil.r.ready
  axil_cc_inst.io.m_axi_aclk := io.m_axil_clock
  axil_cc_inst.io.m_axi_aresetn := !io.m_axil_reset.asBool
  io.m_axil.aw.bits.addr := axil_cc_inst.io.m_axi_awaddr
  io.m_axil.aw.bits.prot := axil_cc_inst.io.m_axi_awprot
  io.m_axil.aw.valid := axil_cc_inst.io.m_axi_awvalid
  axil_cc_inst.io.m_axi_awready := io.m_axil.aw.ready
  io.m_axil.w.bits.data := axil_cc_inst.io.m_axi_wdata
  io.m_axil.w.bits.strb := axil_cc_inst.io.m_axi_wstrb
  io.m_axil.w.valid := axil_cc_inst.io.m_axi_wvalid
  axil_cc_inst.io.m_axi_wready := io.m_axil.w.ready
  axil_cc_inst.io.m_axi_bresp := io.m_axil.b.bits.resp
  axil_cc_inst.io.m_axi_bvalid := io.m_axil.b.valid
  io.m_axil.b.ready := axil_cc_inst.io.m_axi_bready
  io.m_axil.ar.bits.addr := axil_cc_inst.io.m_axi_araddr
  io.m_axil.ar.bits.prot := axil_cc_inst.io.m_axi_arprot
  io.m_axil.ar.valid := axil_cc_inst.io.m_axi_arvalid
  axil_cc_inst.io.m_axi_arready := io.m_axil.ar.ready
  axil_cc_inst.io.m_axi_rdata := io.m_axil.r.bits.data
  axil_cc_inst.io.m_axi_rresp := io.m_axil.r.bits.resp
  axil_cc_inst.io.m_axi_rvalid := io.m_axil.r.valid
  io.m_axil.r.ready := axil_cc_inst.io.m_axi_rready
}