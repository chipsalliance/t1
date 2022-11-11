package tests.elaborate

import chisel3._
import chisel3.experimental.ExtModule
import chisel3.util.HasExtModuleInline
import v.V

class VerificationModule(dut: V, dutFlattenIO: Seq[(String, Data)]) extends TapModule {
  override val desiredName = "VerificationModule"
  val dpiFire = Module(new ExtModule with HasExtModuleInline {
    override val desiredName = "dpiFire"
    val cond = IO(Input(Bool()))
    setInline("dpiFire.sv",
      """module dpiFire(input cond);
        |import "DPI-C" function void dpiFire();
        |always @ (posedge cond) dpiFire();
        |endmodule
        |""".stripMargin
    )
  })
  val clock = IO(Output(Clock()))
  val reset = IO(Output(Bool()))
  val xmrFlattenIO = dutFlattenIO.map({ case (name, port) =>
    val io = chisel3.experimental.DataMirror.directionOf(port) match {
      case ActualDirection.Output =>
        IO(Input(chiselTypeOf(port))).suggestName(name)
      case ActualDirection.Input =>
        val o = IO(Output(chiselTypeOf(port))).suggestName(name)
        dontTouch(o)
        o := DontCare
        o
    }
    io
  })
  val verbatim = Module(new ExtModule with HasExtModuleInline {
    override val desiredName = "Verbatim"
    val clock = IO(Output(Clock()))
    val reset = IO(Output(Bool()))
    setInline("verbatim.sv",
      """module Verbatim(
        |output clock,
        |output reset
        |);
        |reg _clock = 1'b0;
        |always #(0.5) _clock = ~_clock;
        |reg _reset = 1'b1;
        |initial #(10.1) _reset = 0;
        |assign clock = _clock;
        |assign reset = _reset;
        |
        |import "DPI-C" function void dpiInitCosim();
        |initial dpiInitCosim();
        |
        |endmodule
        |""".stripMargin)
  })
  clock := verbatim.clock
  reset := verbatim.reset

  // XMR
  val instCount = tap(dut.instCount)
  val laneWriteReadySeq = dut.laneVec.map(_.vrf.write.ready).map(tap)
  val laneWriteValidSeq = dut.laneVec.map(_.vrf.write.valid).map(tap)
  val laneWriteBitsSeq = dut.laneVec.map(_.vrf.write.bits).map(tap)
  val lsuReqEnqDbg = withClockAndReset(clock, reset)(RegNext(tap(dut.lsu.reqEnq)))

  dpiFire.cond := tap(dut.req.valid) && tap(dut.req.ready)
  done()
}
