package org.chipsalliance.t1.ipemu

import chisel3.{Bool, Clock, Output}
import chisel3.experimental.ExtModule
import chisel3.probe._
import chisel3.util.HasExtModuleInline

case class ClockGenParameter(clockRate: Int)

class ClockGen(val parameter: ClockGenParameter)
  extends ExtModule
    with HasExtModuleInline
    with HasExtModuleDefine {
  setInline(s"$desiredName.sv",
    s"""module $desiredName(output reg clock, output reg reset);
       |  initial begin
       |    clock = 1'b0;
       |    reset = 1'b1;
       |  end
       |  initial #(${2 * parameter.clockRate + 1}) reset = 1'b0;
       |  always #(${parameter.clockRate}) clock = ~clock;
       |endmodule
       |""".stripMargin
  )
  val clock = IO(Output(Bool()))
  val reset = IO(Output(Bool()))
}
