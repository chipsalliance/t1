// Temporary SRAM1RW workaround for zaozi VerilogWrapper.
// Will be replaced by zaozi's native SRAM implementation.
// Reference: chisel3/util/SRAM.scala SRAMBlackbox
module SRAM1RW #(
  parameter depth,
  parameter width,
  parameter addrWidth
)(
  input                  clock,
  input                  enable,
  input                  isWrite,
  input  [addrWidth-1:0] address,
  input  [width-1:0]     writeData,
  output [width-1:0]     readData
);

  reg [width-1:0] Memory [0:depth-1];
  reg [addrWidth-1:0] addr_reg;
  reg                  enable_reg;

  always @(posedge clock) begin
    if (enable) begin
      if (isWrite)
        Memory[address] <= writeData;
      addr_reg   <= address;
      enable_reg <= enable;
    end else begin
      enable_reg <= 1'b0;
    end
  end

  assign readData = enable_reg ? Memory[addr_reg] : {width{1'bx}};

endmodule
