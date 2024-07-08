package org.chipsalliance.t1.rocketv

import chisel3.{Bool, Clock, Output}
import chisel3.experimental.ExtModule
import chisel3.probe._
import chisel3.util.HasExtModuleInline

class ClockGen extends ExtModule with HasExtModuleInline {
  setInline(s"$desiredName.sv",
    s"""module $desiredName(output reg clock, output reg reset);
       |  initial begin
       |    clock = 1'b0;
       |    reset = 1'b1;
       |  end
       |  initial #(11) reset = 1'b0;
       |  always #10 clock = ~clock;
       |endmodule
       |""".stripMargin
  )
  val clock = IO(Output(Bool()))
  val reset = IO(Output(Bool()))
}
