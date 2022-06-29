package v

import chisel3._

class Vector extends Module {
  val in: Bool = IO(Input(Bool()))
  val out: Bool = IO(Output(Bool()))
  out := in
}