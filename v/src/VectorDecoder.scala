package v

import chisel3._
import chisel3.util.experimental.decode._

class VectorDecoder extends Module {
  val decodeInput:  UInt = IO(Input(UInt(21.W)))
  val decodeResult: DecodeBundle = IO(Output(new DecodeBundle(Decoder.all)))

  decodeResult := Decoder.decode(decodeInput)
}
