import chisel3._
import chisel3.experimental.ExtModule
import chisel3.util.HasExtModuleInline

trait DPI extends ExtModule with HasExtModuleInline {
  val io = IO(new Bundle {
    val a = Input(UInt(32.W))
    val b = Input(UInt(32.W))
    val c = Output(UInt(32.W))
  })
  setInline("DPI.v",
    """module DPI(
      |input [31:0] a,
      |input [31:0] b,
      |output [31:0] c
      |);
      |integer a_int;
      |integer b_int;
      |integer c_int;
      |assign a_int = a;
      |assign b_int = b;
      |assign c_int = a_int + b_int;
      |assign c = c_int;
      |endmodule
      |""".stripMargin)
}
