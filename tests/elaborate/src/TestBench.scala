package tests.elaborate

import chisel3._
import v.{V, VParam}

class TestBench extends RawModule {
  val clock = Wire(Clock())
  val reset = Wire(Bool())
  val dut = withClockAndReset(clock, reset) {
    Module(new V(VParam()))
  }
  val dutFlattenIO = chisel3.experimental.DataMirror.fullModulePorts(dut).filterNot(_._2.isInstanceOf[Aggregate]).filterNot(d => d._1 == "clock" || d._1 == "reset")
  val verificationModule = Module(new VerificationModule(dut, dutFlattenIO))
  dutFlattenIO zip verificationModule.xmrFlattenIO foreach { case ((_, dutPort), xmrPort) =>
    chisel3.experimental.DataMirror.directionOf(dutPort) match {
      case ActualDirection.Output =>
        xmrPort := dutPort
      case ActualDirection.Input =>
        dutPort := xmrPort
    }
  }
  clock := verificationModule.clock
  reset := verificationModule.reset
}
