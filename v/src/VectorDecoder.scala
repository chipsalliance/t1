package v

import chisel3._
import chisel3.util.experimental.decode._

class VectorDecoder(fpuEnable: Boolean) extends Module {
  val decodeInput: UInt = IO(Input(UInt(21.W)))
  val decodeResult: DecodeBundle = IO(Output(new DecodeBundle(Decoder.all(fpuEnable))))

  decodeResult := Decoder.decode(fpuEnable)(decodeInput)
}
