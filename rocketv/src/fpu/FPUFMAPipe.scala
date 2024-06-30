// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package freechips.rocketchip.tile

import chisel3.util.{Pipe, Valid}
import chisel3.{Bundle, Flipped, Module, Reg, RegNext, Wire, when}
import freechips.rocketchip.tile.{FPInput, FPResult, FPUModule, FType, MulAddRecFNPipe}

class FPUFMAPipe(val latency: Int, val t: FType)
                (implicit p: Parameters) extends FPUModule()(p) with ShouldBeRetimed {
  require(latency>0)

  val io = IO(new Bundle {
    val in = Flipped(Valid(new FPInput))
    val out = Valid(new FPResult)
  })

  val valid = RegNext(io.in.valid)
  val in = Reg(new FPInput)
  when (io.in.valid) {
    val one = 1.U << (t.sig + t.exp - 1)
    val zero = (io.in.bits.in1 ^ io.in.bits.in2) & (1.U << (t.sig + t.exp))
    val cmd_fma = io.in.bits.ren3
    val cmd_addsub = io.in.bits.swap23
    in := io.in.bits
    when (cmd_addsub) { in.in2 := one }
    when (!(cmd_fma || cmd_addsub)) { in.in3 := zero }
  }

  val fma = Module(new MulAddRecFNPipe((latency-1) min 2, t.exp, t.sig))
  fma.io.validin := valid
  fma.io.op := in.fmaCmd
  fma.io.roundingMode := in.rm
  fma.io.detectTininess := hardfloat.consts.tininess_afterRounding
  fma.io.a := in.in1
  fma.io.b := in.in2
  fma.io.c := in.in3

  val res = Wire(new FPResult)
  res.data := sanitizeNaN(fma.io.out, t)
  res.exc := fma.io.exceptionFlags

  io.out := Pipe(fma.io.validout, res, (latency-3) max 0)
}
