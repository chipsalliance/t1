package verdes

import chisel3._
import chisel3.probe._
import freechips.rocketchip.devices.debug.Debug
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.subsystem.{ExtBus, ExtMem}
import freechips.rocketchip.util.AsyncResetReg
import org.chipsalliance.cde.config.Parameters

class TestHarness(implicit val p: Parameters) extends RawModule {
  val ldut = LazyModule(new VerdesSystem)
  val dpiClockGen = Module(new ClockGen(ClockGenParameter(2)))
  val clock = read(dpiClockGen.clock)
  val reset = read(dpiClockGen.reset)

  withClockAndReset(clock.asClock, reset) {
    val dut = Module(ldut.module)
    // Allow the debug ndreset to reset the dut, but not until the initial reset has completed
    dut.reset := (reset.asBool | ldut.debug.map { debug => AsyncResetReg(debug.ndreset) }.getOrElse(false.B)).asBool
    Debug.tieoffDebug(ldut.debug, ldut.resetctrl, Some(ldut.psd))
    dut.dontTouchPorts()

    ldut.mem_axi4.zip(ldut.memAXI4Node.in).map { case (io, (_, edge)) =>
      val mem = LazyModule(new LazyAXI4MemBFM(edge, base = p(ExtMem).get.master.base, size = p(ExtMem).get.master.size))
      Module(mem.module).suggestName("mem")
      mem.io_axi4.head <> io
      mem
    }.toSeq
  }
}
