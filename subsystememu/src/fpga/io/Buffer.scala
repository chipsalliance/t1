package verdes.fpga.io

import chisel3._
import freechips.rocketchip.util.ElaborationArtefacts

class IBUFDSGTE_PCIe extends BlackBox {
  val io = IO(new Bundle {
    val IBUF_DS_P = Input(Bool())
    val IBUF_DS_N = Input(Bool())
    val IBUF_OUT = Output(Clock())
    val IBUF_DS_ODIV2 = Output(Clock())
  })
  ElaborationArtefacts.add(desiredName + ".tcl",
    s"""
       |create_ip -name util_ds_buf -vendor xilinx.com -library ip -version 2.2 -module_name $desiredName
       |set_property -dict [list \\
       |  CONFIG.C_BUF_TYPE {IBUFDSGTE} \\
       |  CONFIG.DIFF_CLK_IN_BOARD_INTERFACE {pcie_refclk} \\
       |] [get_ips $desiredName]
       |""".stripMargin)
}