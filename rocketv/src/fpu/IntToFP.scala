// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package freechips.rocketchip.tile

import chisel3._
import chisel3.util.{Cat, Pipe, Valid, log2Ceil}
import freechips.rocketchip.tile.{FPResult, FPUModule, IntToFPInput}

class IntToFP(val latency: Int)(implicit p: Parameters) extends FPUModule()(p) with ShouldBeRetimed {
  val io = IO(new Bundle {
    val in = Flipped(Valid(new IntToFPInput))
    val out = Valid(new FPResult)
  })

  val in = Pipe(io.in)
  val tag = in.bits.typeTagIn

  val mux = Wire(new FPResult)
  mux.exc := 0.U
  mux.data := recode(in.bits.in1, tag)

  val intValue = {
    val res = WireDefault(in.bits.in1.asSInt)
    for (i <- 0 until nIntTypes-1) {
      val smallInt = in.bits.in1((minXLen << i) - 1, 0)
      when (in.bits.typ.extract(log2Ceil(nIntTypes), 1) === i.U) {
        res := Mux(in.bits.typ(0), smallInt.zext, smallInt.asSInt)
      }
    }
    res.asUInt
  }

  when (in.bits.wflags) { // fcvt
    // could be improved for RVD/RVQ with a single variable-position rounding
    // unit, rather than N fixed-position ones
    val i2fResults = for (t <- floatTypes) yield {
      val i2f = Module(new hardfloat.INToRecFN(xLen, t.exp, t.sig))
      i2f.io.signedIn := ~in.bits.typ(0)
      i2f.io.in := intValue
      i2f.io.roundingMode := in.bits.rm
      i2f.io.detectTininess := hardfloat.consts.tininess_afterRounding
      (sanitizeNaN(i2f.io.out, t), i2f.io.exceptionFlags)
    }

    val (data, exc) = i2fResults.unzip
    val dataPadded = data.init.map(d => Cat(data.last >> d.getWidth, d)) :+ data.last
    mux.data := dataPadded(tag)
    mux.exc := exc(tag)
  }

  io.out <> Pipe(in.valid, mux, latency-1)
}
