module ClockGen(output reg clock, output reg reset);
`ifdef T1_ENABLE_TRACE
  export "DPI-C" function dump_wave;
  function dump_wave(input string file);
`ifdef VCS
    $fsdbDumpfile(file);
    $fsdbDumpvars("+all");
    $fsdbDumpon;
`endif
`ifdef VERILATOR
    $dumpfile(file);
    $dumpvars(0);
`endif
  endfunction;
`endif

  export "DPI-C" function quit;
  function quit();
    $finish;
  endfunction;

  import "DPI-C" context function void t1rocket_cosim_init();
  import "DPI-C" context function void t1rocket_cosim_final();
  initial begin
    t1rocket_cosim_init();
    clock = 1'b0;
    reset = 1'b1;
  end
  final begin
    t1rocket_cosim_final();
  end

  initial #(100) reset = 1'b0;
  always #10 clock = ~clock;
endmodule
