package verdes.fpga.io

import chisel3._
import freechips.rocketchip.amba.axi4.{AXI4Bundle, AXI4BundleParameters}
import freechips.rocketchip.util.ElaborationArtefacts

class AXIInterConnectBlackBox(
  val addrWidth: Int,
  val slaveIDWidth: Int,
  val dataWidth: Int,
  val MdataWidth: Int,
  val S0dataWidth: Int,
  val S1dataWidth: Int) extends BlackBox {
  val io = IO(new Bundle{
    val INTERCONNECT_ACLK = Input(Clock())
    val INTERCONNECT_ARESETN = Input(Bool())
    val S00_AXI_ARESET_OUT_N = Output(Bool())
    val S00_AXI_ACLK = Input(Clock())
    val S00_AXI_AWID = Input(UInt(slaveIDWidth.W))
    val S00_AXI_AWADDR = Input(UInt(addrWidth.W))
    val S00_AXI_AWLEN = Input(UInt(8.W))
    val S00_AXI_AWSIZE = Input(UInt(3.W))
    val S00_AXI_AWBURST = Input(UInt(2.W))
    val S00_AXI_AWLOCK = Input(Bool())
    val S00_AXI_AWCACHE = Input(UInt(4.W))
    val S00_AXI_AWPROT = Input(UInt(3.W))
    val S00_AXI_AWQOS = Input(UInt(4.W))
    val S00_AXI_AWVALID = Input(Bool())
    val S00_AXI_AWREADY = Output(Bool())
    val S00_AXI_WDATA = Input(UInt(S0dataWidth.W))
    val S00_AXI_WSTRB = Input(UInt((S0dataWidth/8).W))
    val S00_AXI_WLAST = Input(Bool())
    val S00_AXI_WVALID = Input(Bool())
    val S00_AXI_WREADY = Output(Bool())
    val S00_AXI_BID = Output(UInt(slaveIDWidth.W))
    val S00_AXI_BRESP = Output(UInt(2.W))
    val S00_AXI_BVALID = Output(Bool())
    val S00_AXI_BREADY = Input(Bool())
    val S00_AXI_ARID = Input(UInt(slaveIDWidth.W))
    val S00_AXI_ARADDR = Input(UInt(addrWidth.W))
    val S00_AXI_ARLEN = Input(UInt(8.W))
    val S00_AXI_ARSIZE = Input(UInt(3.W))
    val S00_AXI_ARBURST = Input(UInt(2.W))
    val S00_AXI_ARLOCK = Input(Bool())
    val S00_AXI_ARCACHE = Input(UInt(4.W))
    val S00_AXI_ARPROT = Input(UInt(3.W))
    val S00_AXI_ARQOS = Input(UInt(4.W))
    val S00_AXI_ARVALID = Input(Bool())
    val S00_AXI_ARREADY = Output(Bool())
    val S00_AXI_RID = Output(UInt(slaveIDWidth.W))
    val S00_AXI_RDATA = Output(UInt(S0dataWidth.W))
    val S00_AXI_RRESP = Output(UInt(2.W))
    val S00_AXI_RLAST = Output(Bool())
    val S00_AXI_RVALID = Output(Bool())
    val S00_AXI_RREADY = Input(Bool())
    val S01_AXI_ARESET_OUT_N = Output(Bool())
    val S01_AXI_ACLK = Input(Clock())
    val S01_AXI_AWID = Input(UInt(slaveIDWidth.W))
    val S01_AXI_AWADDR = Input(UInt(addrWidth.W))
    val S01_AXI_AWLEN = Input(UInt(8.W))
    val S01_AXI_AWSIZE = Input(UInt(3.W))
    val S01_AXI_AWBURST = Input(UInt(2.W))
    val S01_AXI_AWLOCK = Input(Bool())
    val S01_AXI_AWCACHE = Input(UInt(4.W))
    val S01_AXI_AWPROT = Input(UInt(3.W))
    val S01_AXI_AWQOS = Input(UInt(4.W))
    val S01_AXI_AWVALID = Input(Bool())
    val S01_AXI_AWREADY = Output(Bool())
    val S01_AXI_WDATA = Input(UInt(S1dataWidth.W))
    val S01_AXI_WSTRB = Input(UInt((S1dataWidth/8).W))
    val S01_AXI_WLAST = Input(Bool())
    val S01_AXI_WVALID = Input(Bool())
    val S01_AXI_WREADY = Output(Bool())
    val S01_AXI_BID = Output(UInt(slaveIDWidth.W))
    val S01_AXI_BRESP = Output(UInt(2.W))
    val S01_AXI_BVALID = Output(Bool())
    val S01_AXI_BREADY = Input(Bool())
    val S01_AXI_ARID = Input(UInt(slaveIDWidth.W))
    val S01_AXI_ARADDR = Input(UInt(addrWidth.W))
    val S01_AXI_ARLEN = Input(UInt(8.W))
    val S01_AXI_ARSIZE = Input(UInt(3.W))
    val S01_AXI_ARBURST = Input(UInt(2.W))
    val S01_AXI_ARLOCK = Input(Bool())
    val S01_AXI_ARCACHE = Input(UInt(4.W))
    val S01_AXI_ARPROT = Input(UInt(3.W))
    val S01_AXI_ARQOS = Input(UInt(4.W))
    val S01_AXI_ARVALID = Input(Bool())
    val S01_AXI_ARREADY = Output(Bool())
    val S01_AXI_RID = Output(UInt(slaveIDWidth.W))
    val S01_AXI_RDATA = Output(UInt(S1dataWidth.W))
    val S01_AXI_RRESP = Output(UInt(2.W))
    val S01_AXI_RLAST = Output(Bool())
    val S01_AXI_RVALID = Output(Bool())
    val S01_AXI_RREADY = Input(Bool())
    val M00_AXI_ARESET_OUT_N = Output(Bool())
    val M00_AXI_ACLK = Input(Clock())
    val M00_AXI_AWID = Output(UInt((slaveIDWidth+4).W))
    val M00_AXI_AWADDR = Output(UInt(addrWidth.W))
    val M00_AXI_AWLEN = Output(UInt(8.W))
    val M00_AXI_AWSIZE = Output(UInt(3.W))
    val M00_AXI_AWBURST = Output(UInt(2.W))
    val M00_AXI_AWLOCK = Output(Bool())
    val M00_AXI_AWCACHE = Output(UInt(4.W))
    val M00_AXI_AWPROT = Output(UInt(3.W))
    val M00_AXI_AWQOS = Output(UInt(4.W))
    val M00_AXI_AWVALID = Output(Bool())
    val M00_AXI_AWREADY = Input(Bool())
    val M00_AXI_WDATA = Output(UInt(MdataWidth.W))
    val M00_AXI_WSTRB = Output(UInt((MdataWidth/8).W))
    val M00_AXI_WLAST = Output(Bool())
    val M00_AXI_WVALID = Output(Bool())
    val M00_AXI_WREADY = Input(Bool())
    val M00_AXI_BID = Input(UInt((slaveIDWidth+4).W))
    val M00_AXI_BRESP = Input(UInt(2.W))
    val M00_AXI_BVALID = Input(Bool())
    val M00_AXI_BREADY = Output(Bool())
    val M00_AXI_ARID = Output(UInt((slaveIDWidth+4).W))
    val M00_AXI_ARADDR = Output(UInt(addrWidth.W))
    val M00_AXI_ARLEN = Output(UInt(8.W))
    val M00_AXI_ARSIZE = Output(UInt(3.W))
    val M00_AXI_ARBURST = Output(UInt(2.W))
    val M00_AXI_ARLOCK = Output(Bool())
    val M00_AXI_ARCACHE = Output(UInt(4.W))
    val M00_AXI_ARPROT = Output(UInt(3.W))
    val M00_AXI_ARQOS = Output(UInt(4.W))
    val M00_AXI_ARVALID = Output(Bool())
    val M00_AXI_ARREADY = Input(Bool())
    val M00_AXI_RID = Input(UInt((slaveIDWidth+4).W))
    val M00_AXI_RDATA = Input(UInt(MdataWidth.W))
    val M00_AXI_RRESP = Input(UInt(2.W))
    val M00_AXI_RLAST = Input(Bool())
    val M00_AXI_RVALID = Input(Bool())
    val M00_AXI_RREADY = Output(Bool())
  })
  ElaborationArtefacts.add(desiredName + ".tcl",
    s"""
       |create_ip -name axi_interconnect -vendor xilinx.com -library ip -version 1.7 -module_name $desiredName
       |set_property -dict [list \\
       |  CONFIG.AXI_ADDR_WIDTH {$addrWidth} \\
       |  CONFIG.INTERCONNECT_DATA_WIDTH {$dataWidth} \\
       |  CONFIG.M00_AXI_DATA_WIDTH {$MdataWidth} \\
       |  CONFIG.M00_AXI_READ_ISSUING {32} \\
       |  CONFIG.M00_AXI_WRITE_ISSUING {32} \\
       |  CONFIG.S00_AXI_DATA_WIDTH {$S0dataWidth} \\
       |  CONFIG.S00_AXI_READ_ACCEPTANCE {32} \\
       |  CONFIG.S00_AXI_WRITE_ACCEPTANCE {32} \\
       |  CONFIG.S01_AXI_DATA_WIDTH {$S1dataWidth} \\
       |  CONFIG.S01_AXI_READ_ACCEPTANCE {32} \\
       |  CONFIG.S01_AXI_WRITE_ACCEPTANCE {32} \\
       |  CONFIG.THREAD_ID_WIDTH {$slaveIDWidth} \\
       |] [get_ips $desiredName]
       |""".stripMargin)
}

class AXIInterConnect(
  val addrWidth: Int = 32,
  val slaveIDWidth: Int = 4,
  val dataWidth: Int = 512,
  val MdataWidth: Int = 512,
  val S0dataWidth: Int = 512,
  val S1dataWidth: Int = 512) extends Module {

  val io = IO(new Bundle {
    val s0_axi_clock = Input(Clock())
    val s0_axi_reset = Output(Reset())
    val s0_axi = Flipped(new AXI4Bundle(new AXI4BundleParameters(addrWidth, S0dataWidth, slaveIDWidth)))
    val s1_axi_clock = Input(Clock())
    val s1_axi_reset = Output(Reset())
    val s1_axi = Flipped(new AXI4Bundle(new AXI4BundleParameters(addrWidth, S1dataWidth, slaveIDWidth)))
    val m0_axi = new AXI4Bundle(new AXI4BundleParameters(addrWidth, MdataWidth, slaveIDWidth + 4))
    val m0_axi_clock = Input(Clock())
    val m0_axi_reset = Output(Reset())
  })

  val inst = Module(new AXIInterConnectBlackBox(addrWidth, slaveIDWidth, dataWidth, MdataWidth, S0dataWidth, S1dataWidth))
  inst.io.INTERCONNECT_ACLK := clock
  inst.io.INTERCONNECT_ARESETN := ~reset.asBool
  inst.io.S00_AXI_ACLK := io.s0_axi_clock
  io.s0_axi_reset := ~inst.io.S00_AXI_ARESET_OUT_N
  inst.io.S00_AXI_AWID := io.s0_axi.aw.bits.id
  inst.io.S00_AXI_AWADDR := io.s0_axi.aw.bits.addr
  inst.io.S00_AXI_AWLEN := io.s0_axi.aw.bits.len
  inst.io.S00_AXI_AWSIZE := io.s0_axi.aw.bits.size
  inst.io.S00_AXI_AWBURST := io.s0_axi.aw.bits.burst
  inst.io.S00_AXI_AWLOCK := io.s0_axi.aw.bits.lock
  inst.io.S00_AXI_AWCACHE := io.s0_axi.aw.bits.cache
  inst.io.S00_AXI_AWPROT := io.s0_axi.aw.bits.prot
  inst.io.S00_AXI_AWQOS := io.s0_axi.aw.bits.qos
  inst.io.S00_AXI_AWVALID := io.s0_axi.aw.valid
  io.s0_axi.aw.ready := inst.io.S00_AXI_AWREADY
  inst.io.S00_AXI_WDATA := io.s0_axi.w.bits.data
  inst.io.S00_AXI_WSTRB := io.s0_axi.w.bits.strb
  inst.io.S00_AXI_WLAST := io.s0_axi.w.bits.last
  inst.io.S00_AXI_WVALID := io.s0_axi.w.valid
  io.s0_axi.w.ready := inst.io.S00_AXI_WREADY
  inst.io.S00_AXI_BREADY := io.s0_axi.b.ready
  io.s0_axi.b.bits.id := inst.io.S00_AXI_BID
  io.s0_axi.b.bits.resp := inst.io.S00_AXI_BRESP
  io.s0_axi.b.valid := inst.io.S00_AXI_BVALID
  inst.io.S00_AXI_ARID := io.s0_axi.ar.bits.id
  inst.io.S00_AXI_ARADDR := io.s0_axi.ar.bits.addr
  inst.io.S00_AXI_ARLEN := io.s0_axi.ar.bits.len
  inst.io.S00_AXI_ARSIZE := io.s0_axi.ar.bits.size
  inst.io.S00_AXI_ARBURST := io.s0_axi.ar.bits.burst
  inst.io.S00_AXI_ARLOCK := io.s0_axi.ar.bits.lock
  inst.io.S00_AXI_ARCACHE := io.s0_axi.ar.bits.cache
  inst.io.S00_AXI_ARPROT := io.s0_axi.ar.bits.prot
  inst.io.S00_AXI_ARQOS := io.s0_axi.ar.bits.qos
  inst.io.S00_AXI_ARVALID := io.s0_axi.ar.valid
  io.s0_axi.ar.ready := inst.io.S00_AXI_ARREADY
  inst.io.S00_AXI_RREADY := io.s0_axi.r.ready
  io.s0_axi.r.bits.id := inst.io.S00_AXI_RID
  io.s0_axi.r.bits.data := inst.io.S00_AXI_RDATA
  io.s0_axi.r.bits.resp := inst.io.S00_AXI_RRESP
  io.s0_axi.r.bits.last := inst.io.S00_AXI_RLAST
  io.s0_axi.r.valid := inst.io.S00_AXI_RVALID
  inst.io.S01_AXI_ACLK := io.s1_axi_clock
  io.s1_axi_reset := ~inst.io.S01_AXI_ARESET_OUT_N
  inst.io.S01_AXI_AWID := io.s1_axi.aw.bits.id
  inst.io.S01_AXI_AWADDR := io.s1_axi.aw.bits.addr
  inst.io.S01_AXI_AWLEN := io.s1_axi.aw.bits.len
  inst.io.S01_AXI_AWSIZE := io.s1_axi.aw.bits.size
  inst.io.S01_AXI_AWBURST := io.s1_axi.aw.bits.burst
  inst.io.S01_AXI_AWLOCK := io.s1_axi.aw.bits.lock
  inst.io.S01_AXI_AWCACHE := io.s1_axi.aw.bits.cache
  inst.io.S01_AXI_AWPROT := io.s1_axi.aw.bits.prot
  inst.io.S01_AXI_AWQOS := io.s1_axi.aw.bits.qos
  inst.io.S01_AXI_AWVALID := io.s1_axi.aw.valid
  io.s1_axi.aw.ready := inst.io.S01_AXI_AWREADY
  inst.io.S01_AXI_WDATA := io.s1_axi.w.bits.data
  inst.io.S01_AXI_WSTRB := io.s1_axi.w.bits.strb
  inst.io.S01_AXI_WLAST := io.s1_axi.w.bits.last
  inst.io.S01_AXI_WVALID := io.s1_axi.w.valid
  io.s1_axi.w.ready := inst.io.S01_AXI_WREADY
  inst.io.S01_AXI_BREADY := io.s1_axi.b.ready
  io.s1_axi.b.bits.id := inst.io.S01_AXI_BID
  io.s1_axi.b.bits.resp := inst.io.S01_AXI_BRESP
  io.s1_axi.b.valid := inst.io.S01_AXI_BVALID
  inst.io.S01_AXI_ARID := io.s1_axi.ar.bits.id
  inst.io.S01_AXI_ARADDR := io.s1_axi.ar.bits.addr
  inst.io.S01_AXI_ARLEN := io.s1_axi.ar.bits.len
  inst.io.S01_AXI_ARSIZE := io.s1_axi.ar.bits.size
  inst.io.S01_AXI_ARBURST := io.s1_axi.ar.bits.burst
  inst.io.S01_AXI_ARLOCK := io.s1_axi.ar.bits.lock
  inst.io.S01_AXI_ARCACHE := io.s1_axi.ar.bits.cache
  inst.io.S01_AXI_ARPROT := io.s1_axi.ar.bits.prot
  inst.io.S01_AXI_ARQOS := io.s1_axi.ar.bits.qos
  inst.io.S01_AXI_ARVALID := io.s1_axi.ar.valid
  io.s1_axi.ar.ready := inst.io.S01_AXI_ARREADY
  inst.io.S01_AXI_RREADY := io.s1_axi.r.ready
  io.s1_axi.r.bits.id := inst.io.S01_AXI_RID
  io.s1_axi.r.bits.data := inst.io.S01_AXI_RDATA
  io.s1_axi.r.bits.resp := inst.io.S01_AXI_RRESP
  io.s1_axi.r.bits.last := inst.io.S01_AXI_RLAST
  io.s1_axi.r.valid := inst.io.S01_AXI_RVALID
  inst.io.M00_AXI_ACLK := io.m0_axi_clock
  io.m0_axi_reset := ~inst.io.M00_AXI_ARESET_OUT_N
  io.m0_axi.aw.bits.id := inst.io.M00_AXI_AWID
  io.m0_axi.aw.bits.addr := inst.io.M00_AXI_AWADDR
  io.m0_axi.aw.bits.len := inst.io.M00_AXI_AWLEN
  io.m0_axi.aw.bits.size := inst.io.M00_AXI_AWSIZE
  io.m0_axi.aw.bits.burst := inst.io.M00_AXI_AWBURST
  io.m0_axi.aw.bits.lock := inst.io.M00_AXI_AWLOCK
  io.m0_axi.aw.bits.cache := inst.io.M00_AXI_AWCACHE
  io.m0_axi.aw.bits.prot := inst.io.M00_AXI_AWPROT
  io.m0_axi.aw.bits.qos := inst.io.M00_AXI_AWQOS
  io.m0_axi.aw.valid := inst.io.M00_AXI_AWVALID
  inst.io.M00_AXI_AWREADY := io.m0_axi.aw.ready
  io.m0_axi.w.bits.data := inst.io.M00_AXI_WDATA
  io.m0_axi.w.bits.strb := inst.io.M00_AXI_WSTRB
  io.m0_axi.w.bits.last := inst.io.M00_AXI_WLAST
  io.m0_axi.w.valid := inst.io.M00_AXI_WVALID
  inst.io.M00_AXI_WREADY := io.m0_axi.w.ready
  inst.io.M00_AXI_BID := io.m0_axi.b.bits.id
  inst.io.M00_AXI_BRESP := io.m0_axi.b.bits.resp
  inst.io.M00_AXI_BVALID := io.m0_axi.b.valid
  io.m0_axi.b.ready := inst.io.M00_AXI_BREADY
  io.m0_axi.ar.bits.id := inst.io.M00_AXI_ARID
  io.m0_axi.ar.bits.addr := inst.io.M00_AXI_ARADDR
  io.m0_axi.ar.bits.len := inst.io.M00_AXI_ARLEN
  io.m0_axi.ar.bits.size := inst.io.M00_AXI_ARSIZE
  io.m0_axi.ar.bits.burst := inst.io.M00_AXI_ARBURST
  io.m0_axi.ar.bits.lock := inst.io.M00_AXI_ARLOCK
  io.m0_axi.ar.bits.cache := inst.io.M00_AXI_ARCACHE
  io.m0_axi.ar.bits.prot := inst.io.M00_AXI_ARPROT
  io.m0_axi.ar.bits.qos := inst.io.M00_AXI_ARQOS
  io.m0_axi.ar.valid := inst.io.M00_AXI_ARVALID
  inst.io.M00_AXI_ARREADY := io.m0_axi.ar.ready
  inst.io.M00_AXI_RID := io.m0_axi.r.bits.id
  inst.io.M00_AXI_RDATA := io.m0_axi.r.bits.data
  inst.io.M00_AXI_RRESP := io.m0_axi.r.bits.resp
  inst.io.M00_AXI_RLAST := io.m0_axi.r.bits.last
  inst.io.M00_AXI_RVALID := io.m0_axi.r.valid
  io.m0_axi.r.ready := inst.io.M00_AXI_RREADY
}