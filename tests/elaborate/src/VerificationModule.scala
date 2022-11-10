package tests.elaborate

import chisel3._
import chisel3.experimental.ExtModule
import chisel3.util.HasExtModuleInline
import v.V

class VerificationModule(dut: V, dutFlattenIO: Seq[(String, Data)]) extends TapModule {
  override val desiredName = "VerificationModule"
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
        |
        |reg _reset = 1'b1;
        |initial #(10.1) _reset = 0;
        |
        |assign clock = _clock;
        |assign reset = _reset;
        |""".stripMargin)
  })
  clock := verbatim.clock
  reset := verbatim.reset

  // XMR
  withClockAndReset(clock, reset) {
    tap(dut.instCount)
    dut.laneVec.map(_.vrf.write.ready).map(tap)
    dut.laneVec.map(_.vrf.write.valid).map(tap)
    dut.laneVec.map(_.vrf.write.bits).map(tap)
    val reqEnqDbg = RegNext(tap(dut.lsu.reqEnq))
    dontTouch(reqEnqDbg)

  }
  done()
}
