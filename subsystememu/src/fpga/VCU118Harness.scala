package verdes.fpga

import chisel3._
import freechips.rocketchip.util.ElaborationArtefacts
import org.chipsalliance.cde.config.Parameters
import verdes.fpga.io._

class FPGAHarness(implicit val p: Parameters) extends RawModule {
  val io = IO(new Bundle{
    val pcie_clk = Input(new DifferentialClock())
    val pcie_presetn = Input(Bool())
    val pcie = new PCIeBundle(PCIeParameters(16))
    val ddr_clk = Input(new DifferentialClock())
    val ddr = new VCU118DDR
    val sys_clk = Input(new DifferentialClock())
  })

  // Modules
  val xdma = Module(new XDMA(512,16, true))
  val ddr = Module(new DDRMIG(512, 8))
  val mmcm = Module(new MMCM(100))
  val midShellClock = mmcm.io.clk_out
  val midShellReset = mmcm.io.out_reset
  val midShell = withClockAndReset(midShellClock, midShellReset) {
    Module(new MidShell)
  }
  val axilcc = Module(new AXILiteClockConverter(32, 32))


  // MMCM
  mmcm.io.clk_in := io.sys_clk

  // DDR
  ddr.io.sys_clk := io.ddr_clk
  ddr.io.sys_reset := !io.pcie_presetn.asBool
  ddr.io.ddr <> io.ddr

  // XDMA
  xdma.io.pcie_clk := io.pcie_clk
  xdma.io.pcie_presetn := io.pcie_presetn
  xdma.io.pcie <> io.pcie

  // socReset control
  val socResetFromMidShell = midShell.io.socReset

  // AXI InterConnect
  val socReset = Wire(Reset())
  withClockAndReset(ddr.io.axi_clock, ddr.io.axi_clock_rst) {
    socReset := RegNext(socResetFromMidShell.asBool, init = true.B)
  }
  val axiintc = withClockAndReset(ddr.io.axi_clock, socReset) {
    Module(new AXIInterConnect(
      addrWidth = 31, slaveIDWidth = 4, dataWidth = 512, MdataWidth = 512, S0dataWidth = 512, S1dataWidth = 32))
  }
  axiintc.io.m0_axi_clock := ddr.io.axi_clock
  axiintc.io.s0_axi_clock := ddr.io.axi_clock
  axiintc.io.s1_axi_clock := ddr.io.axi_clock
  axiintc.io.m0_axi <> ddr.io.axi_slave
  ddr.io.axi_reset := axiintc.io.m0_axi_reset
  val s0_cc = Module(new AXIClockConverter(31, 512, 4))
  s0_cc.io.s_axi <> xdma.io.axi_mm
  s0_cc.io.s_axi_clock := xdma.io.axi_mm_clk
  s0_cc.io.s_axi_reset := xdma.io.axi_mm_rst
  s0_cc.io.m_axi <> axiintc.io.s0_axi
  s0_cc.io.m_axi_clock := ddr.io.axi_clock
  s0_cc.io.m_axi_reset := axiintc.io.s0_axi_reset
  val s1_cc = Module(new AXIClockConverter(31, 32, 4))
  s1_cc.io.s_axi <> midShell.io.mem
  s1_cc.io.s_axi_clock := midShellClock
  s1_cc.io.s_axi_reset := socResetFromMidShell
  s1_cc.io.m_axi <> axiintc.io.s1_axi
  s1_cc.io.m_axi_clock := ddr.io.axi_clock
  s1_cc.io.m_axi_reset := axiintc.io.s1_axi_reset

  // AXI Lite Clock Converter
  axilcc.io.s_axil_clock := xdma.io.axi_mm_clk
  axilcc.io.s_axil_reset := xdma.io.axi_mm_rst
  axilcc.io.s_axil <> xdma.io.axil_mmio.get
  axilcc.io.m_axil_clock := midShellClock
  axilcc.io.m_axil_reset := midShellReset
  axilcc.io.m_axil <> midShell.io.mmio

  // MidShell
  val s0_available = withClockAndReset(ddr.io.axi_clock, ddr.io.axi_reset) {
    RegNext(!axiintc.io.s0_axi_reset.asBool)
  }
  val s1_available = withClockAndReset(ddr.io.axi_clock, ddr.io.axi_reset) {
    RegNext(!axiintc.io.s1_axi_reset.asBool)
  }
  val socAvailable = withClockAndReset(midShellClock, midShellReset) {
    RegNext(RegNext(s0_available, init=false.B) && RegNext(s1_available, init=false.B), false.B)
  }
  xdma.io.irq_req := midShell.io.irq
  midShell.io.socAvailable := socAvailable

  ElaborationArtefacts.add( desiredName + ".xdc",
    s"""
       |set_false_path -to [get_pins socAvailable_REG_reg/D]
       |set_property PACKAGE_PIN BB2 [get_ports {io_pcie_rxp[15]}]
       |set_property PACKAGE_PIN BB1 [get_ports {io_pcie_rxn[15]}]
       |set_property PACKAGE_PIN BE5 [get_ports {io_pcie_txp[15]}]
       |set_property PACKAGE_PIN BE4 [get_ports {io_pcie_txn[15]}]
       |set_property PACKAGE_PIN AY2 [get_ports {io_pcie_rxp[14]}]
       |set_property PACKAGE_PIN AY1 [get_ports {io_pcie_rxn[14]}]
       |set_property PACKAGE_PIN BC5 [get_ports {io_pcie_txp[14]}]
       |set_property PACKAGE_PIN BC4 [get_ports {io_pcie_txn[14]}]
       |set_property PACKAGE_PIN AV2 [get_ports {io_pcie_rxp[13]}]
       |set_property PACKAGE_PIN AV1 [get_ports {io_pcie_rxn[13]}]
       |set_property PACKAGE_PIN BA5 [get_ports {io_pcie_txp[13]}]
       |set_property PACKAGE_PIN BA4 [get_ports {io_pcie_txn[13]}]
       |set_property PACKAGE_PIN AT2 [get_ports {io_pcie_rxp[12]}]
       |set_property PACKAGE_PIN AT1 [get_ports {io_pcie_rxn[12]}]
       |set_property PACKAGE_PIN AW5 [get_ports {io_pcie_txp[12]}]
       |set_property PACKAGE_PIN AW4 [get_ports {io_pcie_txn[12]}]
       |set_property PACKAGE_PIN AP2 [get_ports {io_pcie_rxp[11]}]
       |set_property PACKAGE_PIN AP1 [get_ports {io_pcie_rxn[11]}]
       |set_property PACKAGE_PIN AU5 [get_ports {io_pcie_txp[11]}]
       |set_property PACKAGE_PIN AU4 [get_ports {io_pcie_txn[11]}]
       |set_property PACKAGE_PIN AM2 [get_ports {io_pcie_rxp[10]}]
       |set_property PACKAGE_PIN AM1 [get_ports {io_pcie_rxn[10]}]
       |set_property PACKAGE_PIN AT7 [get_ports {io_pcie_txp[10]}]
       |set_property PACKAGE_PIN AT6 [get_ports {io_pcie_txn[10]}]
       |set_property PACKAGE_PIN AK2 [get_ports {io_pcie_rxp[9]}]
       |set_property PACKAGE_PIN AK1 [get_ports {io_pcie_rxn[9]}]
       |set_property PACKAGE_PIN AR5 [get_ports {io_pcie_txp[9]}]
       |set_property PACKAGE_PIN AR4 [get_ports {io_pcie_txn[9]}]
       |set_property PACKAGE_PIN AJ4 [get_ports {io_pcie_rxp[8]}]
       |set_property PACKAGE_PIN AJ3 [get_ports {io_pcie_rxn[8]}]
       |set_property PACKAGE_PIN AP7 [get_ports {io_pcie_txp[8]}]
       |set_property PACKAGE_PIN AP6 [get_ports {io_pcie_txn[8]}]
       |set_property PACKAGE_PIN AH2 [get_ports {io_pcie_rxp[7]}]
       |set_property PACKAGE_PIN AH1 [get_ports {io_pcie_rxn[7]}]
       |set_property PACKAGE_PIN AN5 [get_ports {io_pcie_txp[7]}]
       |set_property PACKAGE_PIN AN4 [get_ports {io_pcie_txn[7]}]
       |set_property PACKAGE_PIN AG4 [get_ports {io_pcie_rxp[6]}]
       |set_property PACKAGE_PIN AG3 [get_ports {io_pcie_rxn[6]}]
       |set_property PACKAGE_PIN AM7 [get_ports {io_pcie_txp[6]}]
       |set_property PACKAGE_PIN AM6 [get_ports {io_pcie_txn[6]}]
       |set_property PACKAGE_PIN AF2 [get_ports {io_pcie_rxp[5]}]
       |set_property PACKAGE_PIN AF1 [get_ports {io_pcie_rxn[5]}]
       |set_property PACKAGE_PIN AK7 [get_ports {io_pcie_txp[5]}]
       |set_property PACKAGE_PIN AK6 [get_ports {io_pcie_txn[5]}]
       |set_property PACKAGE_PIN AE4 [get_ports {io_pcie_rxp[4]}]
       |set_property PACKAGE_PIN AE3 [get_ports {io_pcie_rxn[4]}]
       |set_property PACKAGE_PIN AH7 [get_ports {io_pcie_txp[4]}]
       |set_property PACKAGE_PIN AH6 [get_ports {io_pcie_txn[4]}]
       |set_property PACKAGE_PIN AD2 [get_ports {io_pcie_rxp[3]}]
       |set_property PACKAGE_PIN AD1 [get_ports {io_pcie_rxn[3]}]
       |set_property PACKAGE_PIN AF7 [get_ports {io_pcie_txp[3]}]
       |set_property PACKAGE_PIN AF6 [get_ports {io_pcie_txn[3]}]
       |set_property PACKAGE_PIN AC4 [get_ports {io_pcie_rxp[2]}]
       |set_property PACKAGE_PIN AC3 [get_ports {io_pcie_rxn[2]}]
       |set_property PACKAGE_PIN AD7 [get_ports {io_pcie_txp[2]}]
       |set_property PACKAGE_PIN AD6 [get_ports {io_pcie_txn[2]}]
       |set_property PACKAGE_PIN AB2 [get_ports {io_pcie_rxp[1]}]
       |set_property PACKAGE_PIN AB1 [get_ports {io_pcie_rxn[1]}]
       |set_property PACKAGE_PIN AB7 [get_ports {io_pcie_txp[1]}]
       |set_property PACKAGE_PIN AB6 [get_ports {io_pcie_txn[1]}]
       |set_property PACKAGE_PIN AA4 [get_ports {io_pcie_rxp[0]}]
       |set_property PACKAGE_PIN AA3 [get_ports {io_pcie_rxn[0]}]
       |set_property PACKAGE_PIN Y7 [get_ports {io_pcie_txp[0]}]
       |set_property PACKAGE_PIN Y6 [get_ports {io_pcie_txn[0]}]
       |""".stripMargin)
}