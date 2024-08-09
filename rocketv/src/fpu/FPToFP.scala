// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package freechips.rocketchip.tile

import chisel3._
import chisel3.util.{Cat, Pipe, Valid}
import freechips.rocketchip.tile.{FPInput, FPResult, FPUModule}

class FPToFP(val latency: Int)(implicit p: Parameters) extends FPUModule()(p) with ShouldBeRetimed {
  val io = IO(new Bundle {
    val in = Flipped(Valid(new FPInput))
    val out = Valid(new FPResult)
    val lt = Input(Bool()) // from FPToInt
  })

  val in = Pipe(io.in)

  val signNum = Mux(in.bits.rm(1), in.bits.in1 ^ in.bits.in2, Mux(in.bits.rm(0), ~in.bits.in2, in.bits.in2))
  val fsgnj = Cat(signNum(fLen), in.bits.in1(fLen-1, 0))

  val fsgnjMux = Wire(new FPResult)
  fsgnjMux.exc := 0.U
  fsgnjMux.data := fsgnj

  when (in.bits.wflags) { // fmin/fmax
    val isnan1 = maxType.isNaN(in.bits.in1)
    val isnan2 = maxType.isNaN(in.bits.in2)
    val isInvalid = maxType.isSNaN(in.bits.in1) || maxType.isSNaN(in.bits.in2)
    val isNaNOut = isnan1 && isnan2
    val isLHS = isnan2 || in.bits.rm(0) =/= io.lt && !isnan1
    fsgnjMux.exc := isInvalid << 4
    fsgnjMux.data := Mux(isNaNOut, maxType.qNaN, Mux(isLHS, in.bits.in1, in.bits.in2))
  }

  val inTag = in.bits.typeTagIn
  val outTag = in.bits.typeTagOut
  val mux = WireDefault(fsgnjMux)
  for (t <- floatTypes.init) {
    when (outTag === typeTag(t).U) {
      mux.data := Cat(fsgnjMux.data >> t.recodedWidth, maxType.unsafeConvert(fsgnjMux.data, t))
    }
  }

  when (in.bits.wflags && !in.bits.ren2) { // fcvt
    if (floatTypes.size > 1) {
      // widening conversions simply canonicalize NaN operands
      val widened = Mux(maxType.isNaN(in.bits.in1), maxType.qNaN, in.bits.in1)
      fsgnjMux.data := widened
      fsgnjMux.exc := maxType.isSNaN(in.bits.in1) << 4

      // narrowing conversions require rounding (for RVQ, this could be
      // optimized to use a single variable-position rounding unit, rather
      // than two fixed-position ones)
      for (outType <- floatTypes.init) when (outTag === typeTag(outType).U && ((typeTag(outType) == 0).B || outTag < inTag)) {
        val narrower = Module(new hardfloat.RecFNToRecFN(maxType.exp, maxType.sig, outType.exp, outType.sig))
        narrower.io.in := in.bits.in1
        narrower.io.roundingMode := in.bits.rm
        narrower.io.detectTininess := hardfloat.consts.tininess_afterRounding
        val narrowed = sanitizeNaN(narrower.io.out, outType)
        mux.data := Cat(fsgnjMux.data >> narrowed.getWidth, narrowed)
        mux.exc := narrower.io.exceptionFlags
      }
    }
  }

  io.out <> Pipe(in.valid, mux, latency-1)
}
