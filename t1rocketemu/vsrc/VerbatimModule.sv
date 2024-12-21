package t1_log_pkg;
  // plusargs:
  //   +t1_rtl_event_off  (optional) set to 1 to disable rtl event recording

  bit log_cond;
  int log_fd;

  int rtl_event_off = 0;

  function automatic void log_open(string log_path);
    $value$plusargs("t1_rtl_event_off=%d", rtl_event_off);
    if (rtl_event_off == 0) begin
      log_fd = $fopen(log_path, "w");
      if (log_fd == 0) $fatal(1, "failed to open rtl event file for write");
      log_cond = 1'b1;
    end
  endfunction

  function automatic void log_close();
    if (log_cond) begin
      $fclose(log_fd);
      log_cond = 1'b0;
      log_fd = 0;
    end
  endfunction
endpackage

package t1_wavedump_pkg;
  // plusargs: present only if trace is enabled
  //   +t1_wave_path      (required) path to wave dump file
  //   +t1_dump_start     (optional) cycle when dump starts, by default it's simulation start, for debug only
  //   +t1_dump_end       (optional) cycle when dump ends, by default is's simulation end, for debug only

  longint unsigned dump_start = 0;
  longint unsigned dump_end = 0;
  string wave_path;

`ifdef T1_ENABLE_TRACE

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

  function void init_trace();
    $value$plusargs("t1_dump_start=%d", dump_start);
    $value$plusargs("t1_dump_end=%d", dump_end);
    $value$plusargs("t1_wave_path=%s", wave_path);

    if (wave_path.len() == 0) $fatal(1, "+t1_elf_file must be set");

    if (dump_start == 0) begin
      dump_wave(wave_path);
    end
  endfunction

  function void trigger_trace(longint unsigned cycle);
    if (cycle == dump_start) begin
      dump_wave(wave_path);
    end
    if (cycle == dump_end) begin
      dump_finish();
    end
  endfunction

`else  // T1_ENABLE_TRACE

  function void init_trace();
    $value$plusargs("t1_wave_path=%s", wave_path);
    if (wave_path.len() != 0) $fatal(1, "trace disabled at compile time, but +t1_elf_file is set");
  endfunction

  function void trigger_trace(longint unsigned cycle);
  endfunction

`endif // T1_ENABLE_TRACE
endpackage

module VerbatimModule #(
  parameter integer T1_DLEN,
  parameter integer T1_VLEN,
  parameter string T1_SPIKE_ISA
)(
  output reg clock,
  output reg reset,
  output reg initFlag,
  input  wire idle
);
  // This module contains everything we can not represent in Chisel currently,
  // including clock gen, plusarg parsing, sim control, etc
  //
  // plusargs:
  //   +t1_elf_file       (required)   path to elf file
  //   +t1_timeout        (optional)   max cycle between two AXI DPI call
  //   +t1_global_timeout (optional)   max cycle for whole simulation, for debug only
  //   +t1_timeout_after_quit (optional)

  longint unsigned cycle = 0;
  longint unsigned quit_cycle = 0;
  longint unsigned global_timeout = 0;
  longint unsigned timeout_after_quit = 10000;
  longint unsigned dpi_timeout = 1000000;
  string elf_file;

  // this function captures the scope
  // DO NOT move it outside the module
  import "DPI-C" context function void t1_cosim_preinit(
    int dlen,
    int vlen,
    string spike_isa
  );

  import "DPI-C" context function void t1_cosim_init(string elf_file);
  import "DPI-C" context function void t1_cosim_set_timeout(longint unsigned timeout);
  import "DPI-C" context function void t1_cosim_final();
  import "DPI-C" context function byte unsigned t1_cosim_watchdog();

  function void init_hook();
    $value$plusargs("t1_elf_file=%s", elf_file);
    $value$plusargs("t1_timeout=%d", dpi_timeout);
    $value$plusargs("t1_timeout_after_quit=%d", timeout_after_quit);

    if (elf_file.len() == 0) $fatal(1, "+t1_elf_file must be set");
    t1_cosim_init(elf_file);
    t1_cosim_set_timeout(dpi_timeout);
  endfunction

  function void cycle_hook();
    if (quit_cycle != 0) begin
      // cosim already quits

      if (idle) begin
        $finish;
      end else if (cycle > quit_cycle + timeout_after_quit) begin
        // cosim already quits, but TestBench does not become idle
        $fatal(1, "TestBench idle timeout");
      end
    end else begin
      // do not call watchdog if cosim already quit

      automatic byte unsigned st = t1_cosim_watchdog();
      if (st == 255) begin
        quit_cycle = cycle;
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
  endfunction
  
  initial begin
    clock = 1'b0;
    reset = 1'b1;
    initFlag = 1'b1;

    // it also init rust's logger, make it the first
    t1_cosim_preinit(T1_DLEN, T1_VLEN, T1_SPIKE_ISA);

    t1_log_pkg::log_open("rtl-event.jsonl");
    t1_wavedump_pkg::init_trace();

    $value$plusargs("t1_global_timeout=%d", global_timeout);

    init_hook();

    #10;
    forever begin
      clock = 1'b1;
      #10;
      clock = 1'b0;
      #10;

      cycle += 1;

      cycle_hook();

      if (cycle == global_timeout) begin
        $fatal(1, "global timeout reached");
      end

      t1_wavedump_pkg::trigger_trace(cycle);
    end  
  end

  final begin
    t1_log_pkg::log_close();
    t1_cosim_final();
  end

  initial #(100) reset = 1'b0;
  initial #(20) initFlag = 1'b0;
endmodule
