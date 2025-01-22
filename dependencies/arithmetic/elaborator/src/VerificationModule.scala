package elaborator

import chisel3._
import chisel3.experimental.ExtModule
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._

class VerificationModule extends RawModule {
  val clockRate = 2

  val clock = IO(Output(Clock()))
  val reset = IO(Output(Bool()))

  val dutPoke = IO(DecoupledIO(new DutPoke(8,24)))
  val dutPeek = IO(Flipped(ValidIO(new DutPeek(8,24))))

  val verbatim = Module(new ExtModule with HasExtModuleInline {
    override val desiredName = "Verbatim"
    val clock = IO(Output(Clock()))
    val reset = IO(Output(Bool()))
    setInline(
      "verbatim.sv",
      s"""module Verbatim(
         |  output clock,
         |  output reset
         |);
         |  reg _clock = 1'b0;
         |  always #($clockRate) _clock = ~_clock;
         |  reg _reset = 1'b1;
         |  initial #(${2 * clockRate + 1}) _reset = 0;
         |
         |  assign clock = _clock;
         |  assign reset = _reset;
         |
         |  import "DPI-C" function void dpiInitCosim();
         |  initial dpiInitCosim();
         |
         |  import "DPI-C" function void dpiReport();
         |  always #(${2 * clockRate + 1}) dpiReport();
         |
         |
         |  export "DPI-C" function dpiDumpWave;
         |  function dpiDumpWave(input string file);
         |   $$dumpfile(file);
         |   $$dumpvars(0);
         |  endfunction;
         |
         |  export "DPI-C" function dpiFinish;
         |  function dpiFinish();
         |   $$finish;
         |  endfunction;
         |
         |  export "DPI-C" function dpiError;
         |  function dpiError(input string what);
         |   $$error(what);
         |  endfunction;
         |
         |endmodule
         |""".stripMargin
    )
  })
  clock := verbatim.clock
  reset := verbatim.reset


  val dpiPeek = Module(new ExtModule with HasExtModuleInline {
    override val desiredName = "dpiPeek"
    val ready = IO(Input(Bool()))
    val clock = IO(Input(Clock()))
    setInline(
      s"$desiredName.sv",
      s"""module $desiredName(
         |  input clock,
         |  input ready
         |);
         |  import "DPI-C" function void $desiredName(input bit ready);
         |
         |  always @ (posedge clock) $desiredName(ready);
         |endmodule
         |""".stripMargin
    )
  })
  dpiPeek.clock := verbatim.clock
  dpiPeek.ready := dutPoke.ready

  val dpiCheck = Module(new ExtModule with HasExtModuleInline {
    override val desiredName = "dpiCheck"
    val clock  = IO(Input(Clock()))
    val valid  = IO(Input(Bool()))
    val result = IO(Input(UInt(32.W)))
    val fflags = IO(Input(UInt(5.W)))

    setInline(
      s"$desiredName.sv",
      s"""module $desiredName(
         |  input clock,
         |  input valid,
         |  input [31:0] result,
         |  input [4:0]  fflags
         |);
         |  import "DPI-C" function void $desiredName(
         |  input bit valid,
         |  input bit[31:0] result,
         |  input bit[4:0]  fflags
         |  );
         |
         |  always @ (posedge clock) #1
         |  if (valid == 1)
         |  $desiredName(
         |  valid,
         |  result,
         |  fflags
         |  );
         |endmodule
         |""".stripMargin
    )
  })
  dpiCheck.clock  := verbatim.clock
  dpiCheck.result := dutPeek.bits.result
  dpiCheck.fflags := dutPeek.bits.fflags
  dpiCheck.valid  := dutPeek.valid

  val dpiPoke = Module(new ExtModule with HasExtModuleInline {
    override val desiredName = "dpiPoke"
    val clock = IO(Input(Clock()))
    val a = IO(Output(UInt(32.W)))
    val b = IO(Output(UInt(32.W)))
    val op = IO(Output(Bool()))
    val rm = IO(Output(UInt(3.W)))
    val valid = IO(Output(Bool()))
    val ready  = IO(Input(Bool()))
    setInline(
      s"$desiredName.sv",
      s"""module $desiredName(
         |  input  ready,
         |  output clock,
         |  output valid,
         |  output [31:0] a,
         |  output [31:0] b,
         |  output op,
         |  output [2:0] rm
         |);
         |
         |  import "DPI-C" function void $desiredName(
         |  output bit valid,
         |  output bit[31:0] a,
         |  output bit[31:0] b,
         |  output bit op,
         |  output bit[2:0]  rm
         |  );
         |
         |  always @ (negedge clock)
         |  if(ready==1)
         |  $desiredName(
         |  valid,
         |  a,
         |  b,
         |  op,
         |  rm);
         |
         |endmodule
         |""".stripMargin
    )
  })
  dpiPoke.clock       := verbatim.clock
  dutPoke.valid             := dpiPoke.valid
  dutPoke.bits.a            := dpiPoke.a
  dutPoke.bits.b            := dpiPoke.b
  dutPoke.bits.op           := dpiPoke.op
  dutPoke.bits.roundingMode := dpiPoke.rm
  dpiPoke.ready  := dutPoke.ready
}
