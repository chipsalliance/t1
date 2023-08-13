package tests.elaborate

import chisel3._
import chisel3.probe._

import scala.collection.SeqMap
/*
* input clock,
input bit writeValid,
input int mshrIdx,
input bit[${enq_data.vd.getWidth - 1}:0] data_vd,
input bit[${enq_data.offset.getWidth - 1}:0] data_offset,
input bit[${enq_data.mask.getWidth - 1}:0] data_mask,
input bit[${enq_data.data.getWidth - 1}:0] data_data,
input bit[${enq_data.instructionIndex.getWidth - 1}:0] data_instructionIndex,
input bit[${enq_data.last.getWidth - 1}:0] data_last,
input bit[${targetLane.getWidth - 1}:0] targetLane

* */
class DPIPeekWriteQueue extends DPIModule {
  override val dpiModuleParameter: DPIModuleParameter = DPIModuleParameter(
    isImport = true,
    dpiName = "dpiPeekWriteQueue",
    SeqMap(
      "writeValid" -> RWProbe(Bool()),
      "mshrIdx" -> RWProbe(UInt(32.W)),
    )
  )
  override val body: String =
    s"""
       |
       |
       |""".stripMargin
}
