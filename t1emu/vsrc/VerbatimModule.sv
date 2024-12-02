module VerbatimModule(output reg clock, output reg reset);

  // This module contains everything we can not represent in Chisel currently,
  // including clock gen, plusarg parsing, sim control, etc
  //
  // plusargs: "T" denotes being present only if trace is enabled 
  //   +t1_elf_file       (required)   path to elf file, parsed in DPI side
  //   +t1_wave_path      (required T) path to wave dump file
  //   +t1_timeout        (optional)   max cycle between two V inst retire, parsed in DPI side
  //   +t1_global_timeout (optional)   max cycle for whole simulation, for debug only
  //   +t1_dump_start     (optional T) cycle when dump starts, by default it's simulation start, for debug only
  //   +t1_dump_end       (optional T) cycle when dump ends, by default is's simulation end, for debug only

  longint unsigned cycle = 0;
  longint unsigned global_timeout = 0;
`ifdef T1_ENABLE_TRACE
  longint unsigned dump_start = 0;
  longint unsigned dump_end = 0;
  string wave_path;

  function void dump_wave(input string file);

  `ifdef VCS
    $fsdbDumpfile(file);
    $fsdbDumpvars("+all");
    $fsdbDumpon;
  `endif
  `ifdef VERILATOR
    $dumpfile(file);
    $dumpvars(0);
  `endif

  endfunction

  function void dump_finish();

  `ifdef VCS
    $fsdbDumpFinish();
  `endif
  `ifdef VERILATOR
    $dumpoff();
  `endif

  endfunction
`endif

  import "DPI-C" context function void t1_cosim_init();
  import "DPI-C" context function void t1_cosim_final();
  import "DPI-C" context function byte unsigned t1_cosim_watchdog();

  initial begin
    clock = 1'b0;
    reset = 1'b1;

  `ifdef T1_ENABLE_TRACE
    $value$plusargs("t1_dump_start=%d", dump_start);
    $value$plusargs("t1_dump_end=%d", dump_end);
    $value$plusargs("t1_wave_path=%s", wave_path);
  `endif
    $value$plusargs("t1_global_timeout=%d", global_timeout);

    t1_cosim_init();

  `ifdef T1_ENABLE_TRACE
    if (dump_start == 0) begin
      dump_wave(wave_path);
    end
  `endif

    #10;
    forever begin
      clock = 1'b1;
      #10;
      clock = 1'b0;
      #10;

      cycle += 1;

      begin
        automatic byte unsigned st = t1_cosim_watchdog();
        if (st == 255) begin
          // quit successfully, only if both DPI and TestBench finish
          $finish;
        end else if (st == 0) begin
          // continue, do nothing here
        end else begin
          // error
          $fatal(1, "watchdog timeout");
        end
      end

      if (cycle == global_timeout) begin
        $fatal(1, "global timeout reached");
      end

    `ifdef T1_ENABLE_TRACE
      if (cycle == dump_start) begin
        dump_wave(wave_path);
      end
      if (cycle == dump_end) begin
        dump_finish();
      end
    `endif
    end  
  end

  final begin
    t1_cosim_final();
  end

  initial #(11) reset = 1'b0;
endmodule
