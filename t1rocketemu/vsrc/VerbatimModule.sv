package __circt_lib_logging;
  // plusargs:
  //   +t1_rtl_event_off  (optional) set to 1 to disable rtl event recording
  //   +t1_rtl_event_path            path to rtl event jsonl file

  int    log_fd;
  string log_path;

  int rtl_event_off = 0;

  class FileDescriptor;
    static function int get(string name);
      if (name == "rtl_event.jsonl")
        return log_fd;

      $fatal(1, $sformatf("unknown log target `%s`", name));
    endfunction
  endclass

  function automatic void log_open();
    $value$plusargs("t1_rtl_event_off=%d", rtl_event_off);
    if (rtl_event_off == 0) begin
      $value$plusargs("t1_rtl_event_path=%s", log_path);
      if (log_path.len() == 0) $fatal(1, "+t1_rtl_event_path must be set");

      log_fd = $fopen(log_path, "w");
      if (log_fd == 0) $fatal(1, "failed to open rtl event file for write");
    end
  endfunction

  function automatic void log_close();
    if (log_fd != 0) begin
      $fclose(log_fd);
      log_fd = 0;
    end
  endfunction
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
  // plusargs: "T" denotes being present only if trace is enabled 
  //   +t1_elf_file       (required)   path to elf file
  //   +t1_wave_path      (required T) path to wave dump file
  //   +t1_timeout        (optional)   max cycle between two AXI DPI call
  //   +t1_global_timeout (optional)   max cycle for whole simulation, for debug only
  //   +t1_timeout_after_quit (optional)
  //   +t1_dump_start     (optional T) cycle when dump starts, by default it's simulation start, for debug only
  //   +t1_dump_end       (optional T) cycle when dump ends, by default is's simulation end, for debug only
  //   +t1_dramsim3_cfg   (optional T) path to the dramsim3 configuration. If absent, use embedded version.
  //                                   use "off" to turn off memory latency
  //   +t1_dramsim3_path  (optional T) path of the output of dramsim3. If absent, one path under temp is created

  longint unsigned cycle = 0;
  longint unsigned quit_cycle = 0;
  longint unsigned global_timeout = 0;
  longint unsigned timeout_after_quit = 10000;
  longint unsigned dpi_timeout = 1000000;
  string elf_file;
  string dramsim3_cfg;
  string dramsim3_path;
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

  import "DPI-C" context function void t1_cosim_init(
    string elf_file,
    int dlen,
    int vlen,
    string spike_isa,
    string ds3_cfg,
    string ds3_path
  );
  import "DPI-C" context function void t1_cosim_set_timeout(longint unsigned timeout);
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
    $value$plusargs("t1_elf_file=%s", elf_file);
    $value$plusargs("t1_timeout=%d", dpi_timeout);
    $value$plusargs("t1_global_timeout=%d", global_timeout);
    $value$plusargs("t1_timeout_after_quit=%d", timeout_after_quit);
    $value$plusargs("t1_dramsim3_cfg=%s", dramsim3_cfg);
    $value$plusargs("t1_dramsim3_path=%s", dramsim3_path);

    if (elf_file.len() == 0) $fatal(1, "+t1_elf_file must be set");

    t1_cosim_init(elf_file, T1_DLEN, T1_VLEN, T1_SPIKE_ISA, dramsim3_cfg, dramsim3_path);
    t1_cosim_set_timeout(dpi_timeout);

    __circt_lib_logging::log_open();

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
    __circt_lib_logging::log_close();
    t1_cosim_final();
  end

  initial #(100) reset = 1'b0;
  initial #(20) initFlag = 1'b0;
endmodule
