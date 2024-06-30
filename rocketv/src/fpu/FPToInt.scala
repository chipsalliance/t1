// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package freechips.rocketchip.tile

import chisel3._
import chisel3.util.{Cat, Fill, RegEnable, Valid, log2Ceil}
import freechips.rocketchip.tile.{FPConstants, FPInput, FPUModule, FType}

class FPToInt(implicit p: Parameters) extends FPUModule()(p) with ShouldBeRetimed {
  class Output extends Bundle {
    val in = new FPInput
    val lt = Bool()
    val store = Bits(fLen.W)
    val toint = Bits(xLen.W)
    val exc = Bits(FPConstants.FLAGS_SZ.W)
  }
  val io = IO(new Bundle {
    val in = Flipped(Valid(new FPInput))
    val out = Valid(new Output)
  })

  val in = RegEnable(io.in.bits, io.in.valid)
  val valid = RegNext(io.in.valid)

  val dcmp = Module(new hardfloat.CompareRecFN(maxExpWidth, maxSigWidth))
  dcmp.io.a := in.in1
  dcmp.io.b := in.in2
  dcmp.io.signaling := !in.rm(1)

  val tag = in.typeTagOut
  val store = (floatTypes.map(t => if (t == FType.H) Fill(maxType.ieeeWidth / minXLen,   ieee(in.in1)(15, 0).sextTo(minXLen))
  else              Fill(maxType.ieeeWidth / t.ieeeWidth, ieee(in.in1)(t.ieeeWidth - 1, 0))): Seq[UInt])(tag)
  val toint = WireDefault(store)
  val intType = WireDefault(in.fmt(0))
  io.out.bits.store := store
  io.out.bits.toint := ((0 until nIntTypes).map(i => toint((minXLen << i) - 1, 0).sextTo(xLen)): Seq[UInt])(intType)
  io.out.bits.exc := 0.U

  when (in.rm(0)) {
    val classify_out = (floatTypes.map(t => t.classify(maxType.unsafeConvert(in.in1, t))): Seq[UInt])(tag)
    toint := classify_out | (store >> minXLen << minXLen)
    intType := false.B
  }

  when (in.wflags) { // feq/flt/fle, fcvt
    toint := (~in.rm & Cat(dcmp.io.lt, dcmp.io.eq)).orR | (store >> minXLen << minXLen)
    io.out.bits.exc := dcmp.io.exceptionFlags
    intType := false.B

    when (!in.ren2) { // fcvt
      val cvtType = in.typ.extract(log2Ceil(nIntTypes), 1)
      intType := cvtType
      val conv = Module(new hardfloat.RecFNToIN(maxExpWidth, maxSigWidth, xLen))
      conv.io.in := in.in1
      conv.io.roundingMode := in.rm
      conv.io.signedOut := ~in.typ(0)
      toint := conv.io.out
      io.out.bits.exc := Cat(conv.io.intExceptionFlags(2, 1).orR, 0.U(3.W), conv.io.intExceptionFlags(0))

      for (i <- 0 until nIntTypes-1) {
        val w = minXLen << i
        when (cvtType === i.U) {
          val narrow = Module(new hardfloat.RecFNToIN(maxExpWidth, maxSigWidth, w))
          narrow.io.in := in.in1
          narrow.io.roundingMode := in.rm
          narrow.io.signedOut := ~in.typ(0)

          val excSign = in.in1(maxExpWidth + maxSigWidth) && !maxType.isNaN(in.in1)
          val excOut = Cat(conv.io.signedOut === excSign, Fill(w-1, !excSign))
          val invalid = conv.io.intExceptionFlags(2) || narrow.io.intExceptionFlags(1)
          when (invalid) { toint := Cat(conv.io.out >> w, excOut) }
          io.out.bits.exc := Cat(invalid, 0.U(3.W), !invalid && conv.io.intExceptionFlags(0))
        }
      }
    }
  }

  io.out.valid := valid
  io.out.bits.lt := dcmp.io.lt || (dcmp.io.a.asSInt < 0.S && dcmp.io.b.asSInt >= 0.S)
  io.out.bits.in := in
}
