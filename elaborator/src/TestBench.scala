package tests.elaborate

import chisel3._
import chisel3.experimental.SerializableModuleGenerator
import chisel3.probe._
import v.{V, VParameter}

class TestBench(generator: SerializableModuleGenerator[V, VParameter]) extends RawModule {
  val clockRate = 10
  val clockGen = Module(new ClockGen(ClockGenParameter(clockRate)))
  val clock = read(clockGen.clock)
  val reset = read(clockGen.reset)

  val dut: V = withClockAndReset(clock, reset)(Module(generator.module()))

  // DPI calls VerificationModule
  val dpiInitCosim = Module(new DPIPeekWriteQueue)
  val dpiTimeoutCheck = Module(new DPITimeoutCheck(DPITimeoutCheckParameter(clockRate)))
  val dpiDumpWave = Module(new DPIDumpWave)
  val dpiFinish = Module(new DPIFinish)

  dut.lsu.writeQueueVec.zipWithIndex.foreach { m =>
    val tpe = chiselTypeOf(m._1.io.enq.bits.data)
  }
  val verificationModule = Module(new VerificationModule(dut))

  dut.request <> verificationModule.req
  dut.response <> verificationModule.resp
  dut.csrInterface <> verificationModule.csrInterface
  dut.storeBufferClear <> verificationModule.storeBufferClear
  dut.memoryPorts <> verificationModule.tlPort
  clock := verificationModule.clock
  reset := verificationModule.reset
}
