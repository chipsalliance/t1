package tests.elaborate

import chisel3._
import chisel3.experimental.SerializableModuleGenerator
import v.{V, VParam}

class TestBench extends RawModule {
  val clock = Wire(Clock())
  val reset = Wire(Bool())
  val generator = SerializableModuleGenerator(
    classOf[V],
    VParam()
  )
  val dut = withClockAndReset(clock, reset) {
    Module(
      generator.module()
    )
  }
  val verificationModule = Module(new VerificationModule(dut))
  dut.req <> verificationModule.req
  dut.resp <> verificationModule.resp
  dut.csrInterface <> verificationModule.csrInterface
  dut.storeBufferClear <> verificationModule.storeBufferClear
  dut.tlPort <> verificationModule.tlPort
  clock := verificationModule.clock
  reset := verificationModule.reset
}
