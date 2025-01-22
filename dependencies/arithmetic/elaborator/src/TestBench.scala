package elaborator

import chisel3._

class TestBench(expWidth: Int, sigWidth: Int) extends RawModule {
  val clock = Wire(Clock())
  val reset = Wire(Bool())
  val dut = withClockAndReset(clock, reset) {
    Module(
      new DUT(expWidth, sigWidth)
    )
  }
  val verificationModule = Module(new VerificationModule)
  clock := verificationModule.clock
  reset := verificationModule.reset

  verificationModule.dutPoke <> dut.input
  verificationModule.dutPeek := dut.output

}


