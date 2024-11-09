module ClockGen(
  output reg clock,
  output reg reset,
  output reg initFlag,
  input  wire idle
);

  longint unsigned cycle = 0;
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

  endfunction;
`endif

  import "DPI-C" context function void t1_cosim_init();
  import "DPI-C" context function void t1_cosim_final();
  import "DPI-C" context function byte unsigned t1_cosim_watchdog();
  
  initial begin
    clock = 1'b0;
    reset = 1'b1;
    initFlag = 1'b1;

`ifdef T1_ENABLE_TRACE
    $value$plusargs("t1_dump_start=%d", dump_start);
    $value$plusargs("t1_dump_end=%d", dump_end);
    $value$plusargs("t1_wave_path=%s", wave_path);
`endif

    // Args:
    //   +t1_elf_file=... : path of elf file
    //   +t1_timeout=... : (optional) max interval of inst commit, counted in cycle
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
          if (idle) begin
            // quit successfully, only if both DPI and TestBench finish
            $finish;
          end
        end else if (st == 0) begin
          // continue, do nothing here
        end else begin
          // error
          $fatal(1, "watchdog timeout");
        end
      end

    `ifdef T1_ENALBE_TRACE
      if (cycle == dump_start) begin
        dump_wave();
      end
      if (cycle == dump_end) begin
        // TODO: currently dump_end is actually timeout
        $fatal(1, "dump_end reached");
      end
    `endif
    end  
  end

  final begin
    t1_cosim_final();
  end

  initial #(100) reset = 1'b0;
  initial #(20) initFlag = 1'b0;
endmodule
