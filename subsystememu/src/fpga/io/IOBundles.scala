package verdes.fpga.io

import chisel3._
import chisel3.experimental.Analog

class DifferentialClock extends Bundle {
  val p = Bool()
  val n = Bool()
}

case class PCIeParameters(
  laneWidth: Int
);

class PCIeBundle(val params: PCIeParameters) extends Bundle {
  val txp = Output(UInt(params.laneWidth.W))
  val txn = Output(UInt(params.laneWidth.W))
  val rxp = Input(UInt(params.laneWidth.W))
  val rxn = Input(UInt(params.laneWidth.W))
}

class VCU118DDR() extends Bundle {
  val act_n = Output(Bool())
  val adr = Output(UInt(17.W))
  val ba = Output(UInt(2.W))
  val bg = Output(UInt(1.W))
  val cke = Output(UInt(1.W))
  val odt = Output(UInt(1.W))
  val cs_n = Output(UInt(1.W))
  val ck_t = Output(UInt(1.W))
  val ck_c = Output(UInt(1.W))
  val reset_n = Output(Bool())
  val dm_dbi_n = Analog(8.W)
  val dq = Analog(64.W)
  val dqs_c = Analog(8.W)
  val dqs_t = Analog(8.W)
}