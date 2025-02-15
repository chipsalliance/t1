package org.chipsalliance.t1.rtl.zvma

import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
import chisel3._

object ZVMAParameter {
  implicit def rw: upickle.default.ReadWriter[ZVMAParameter] = upickle.default.macroRW
}

case class ZVMAParameter(
                          vlen: Int,
                          dlen: Int,
                          TE: Int
                        ) extends SerializableModuleParameter {
  val tmWidth: Int = log2Ceil(TE + 1)
  val tnWidth: Int = log2Ceil(vlen + 1)

  val dataIndexBit: Int = log2Ceil(vlen * 8 / dlen + 1)
}

class ZVMCsrInterface(parameter: ZVMAParameter) extends Bundle {
  // TEW = SEW * TWIDEN
  val tew = UInt(3.W)
  // tk can hold values from 0-4, inclusive.
  val tk = UInt(3.W)
  // tm can hold values from 0-TE, inclusive.
  val tm = UInt(parameter.tmWidth.W)
  val tn = UInt(parameter.tnWidth.W)
}

class ZVMAInstRequest(parameter: ZVMAParameter) extends Bundle {
  val instruction: UInt = UInt(32.W)
  val csr = new ZVMCsrInterface(parameter)
}

class ZVMADataChannel(parameter: ZVMAParameter) extends Bundle {
  // The data will be converted into segment format in lsu
  val data: UInt = UInt(parameter.dlen.W)

  val index: UInt = UInt(parameter.dataIndexBit.W)
}

class ZVMAInterface(parameter: ZVMAParameter) extends Bundle {
  val clock          = Input(Clock())
  val reset          = Input(Reset())
  val request: ValidIO[ZVMAInstRequest] = Flipped(Valid(new ZVMAInstRequest(parameter)))
  val dataFromLSU: ValidIO[ZVMADataChannel] = Flipped(Valid(new ZVMADataChannel(parameter)))
  val dataToLSU: DecoupledIO[ZVMADataChannel] = Decoupled(new ZVMADataChannel(parameter))
  val idle: Bool = Output(Bool())
}

class ZVMA(val parameter: ZVMAParameter)
  extends FixedIORawModule(new ZVMAInterface(parameter))
  with SerializableModule[ZVMAParameter]
  with ImplicitClock
  with ImplicitReset {
  protected def implicitClock = io.clock
  protected def implicitReset = io.reset

  val instReg: ZVMAInstRequest = RegEnable(io.request.bits, 0.U.asTypeOf(io.request.bits), io.request.fire)
}
