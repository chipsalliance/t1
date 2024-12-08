package t1_common;
  typedef byte      int8_t;
  typedef shortint  int16_t;
  typedef int       int32_t;
  typedef longint   int64_t;
  typedef byte      unsigned uint8_t;
  typedef shortint  unsigned uint16_t;
  typedef int       unsigned uint32_t;
  typedef longint   unsigned uint64_t;

  typedef uint32_t  axi_tag_t;
  import "DPI-C" function axi_tag_t t1_axi_get_id(
    string tag,
    uint32_t width,
    string direction
  );
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
  import t1_common::*;

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

  uint64_t cycle = 0;
  uint64_t quit_cycle = 0;
  uint64_t global_timeout = 0;
  uint64_t timeout_after_quit = 10000;
  uint64_t dpi_timeout = 1000000;
  string elf_file;
`ifdef T1_ENABLE_TRACE
  uint64_t dump_start = 0;
  uint64_t dump_end = 0;
  string wave_path;

  function void dump_wave(string file);

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
    string spike_isa
  );
  import "DPI-C" context function void t1_cosim_set_timeout(longint unsigned timeout);
  import "DPI-C" context function void t1_cosim_final();
  import "DPI-C" context function uint8_t t1_cosim_watchdog();
  
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

    if (elf_file.len() == 0) $fatal(1, "+t1_elf_file must be set");

    t1_cosim_init(elf_file, T1_DLEN, T1_VLEN, T1_SPIKE_ISA);
    t1_cosim_set_timeout(dpi_timeout);

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

        automatic uint8_t st = t1_cosim_watchdog();
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
    t1_cosim_final();
  end

  initial #(100) reset = 1'b0;
  initial #(20) initFlag = 1'b0;
endmodule

module VerifAxiWrite(
  input   bit         clock,
          bit         reset,
          bit         awfire,
          bit   [7:0] awlen,
          bit         wfire,
          bit         wlast,
          bit         bfire
);
  // check transaction level state machine
  //
  // TODO: current we enforce AW comes before W (or the same cycle)

  int pending_trans[$];
  int pending_beat;
  int pending_ack;

no_fire_at_reset:
  assert property (@(posedge clock) reset |-> !awfire && !wfire && !bfire);

no_pending_at_reset:
  assert property (@(posedge clock) reset |-> pending_trans.size() == 0 && pending_beat == 0);

  always @(posedge clock) begin
    if (awfire) begin
      pending_trans.push_back(int'(awlen) + 1);
    end

    if (wfire) begin
      if (pending_beat == 0) begin
        assert_wfire: assert (pending_trans.size() > 0);

        pending_beat = pending_trans.pop_front();
      end

      assert_wlast: assert (wlast == (pending_beat == 1));

      pending_beat -= 1;
      if (pending_beat == 0) begin
        pending_ack += 1;
      end
    end

    if (bfire) begin
      assert_bfire: assert (pending_ack > 0);
      pending_ack -= 1;
    end
  end
endmodule

module DpiAxiWrite #(
  parameter string TAG,
  parameter integer WIDTH
)(
  input   logic         clock,
          logic         reset,

  input   logic         awvalid,
          logic  [31:0] awaddr,
          logic   [7:0] awlen,
          logic   [2:0] awsize,
  output  logic         awready,

  input   logic         wvalid,
  input   logic [WIDTH-1:0]   wdata,
  input   logic [WIDTH/8-1:0] wstrobe,
  input   logic         wlast,
  output  logic         wready,

  output  logic         bvalid,
          logic   [1:0] bresp,
  input   logic         bready
);
  import t1_common::*;

  if (!(WIDTH inside {8, 16, 32, 64, 128, 256, 512, 1024}))
    $fatal(1, "invalid AXI width");

  // delay to avoid race with init
  axi_tag_t TAG_ID;
  initial #1 TAG_ID = t1_axi_get_id(TAG, WIDTH, "W");

  import "DPI-C" function bit t1_axi_aw_ready(axi_tag_t tag);
  import "DPI-C" function bit t1_axi_aw_transfer(
    axi_tag_t tag,
    uint32_t awaddr,
    uint8_t awlen,
    uint8_t awsize
  );
  import "DPI-C" function bit t1_axi_w_ready(axi_tag_t tag);
  import "DPI-C" function bit t1_axi_w_transfer(
    axi_tag_t tag,
    bit wlast,
    uint8_t[] wdata,
    bit[] wstrobe
  );
  import "DPI-C" function bit t1_axi_b_valid(
    axi_tag_t tag,
    bit is_transfer,
    output uint8_t bresp
  );

  wire awfire = awvalid && awready;
  wire wfire = wvalid && wready;
  wire bfire = bvalid && bready;

  // AW channel
  always @(posedge clock) begin
    if (reset) begin
      awready <= 1'b0;
    end
    else begin
      if (awready) begin
        if (awvalid) begin
          awready <= t1_axi_aw_transfer(
            TAG_ID,
            awaddr,
            awlen,
            awsize
          );
        end
      end else begin
        awready <= t1_axi_aw_ready(TAG_ID);
      end
    end
  end

  // W channel
  always @(posedge clock) begin
    if (reset) begin
      wready <= 1'b0;
    end
    else begin
      if (wready) begin
        if (wvalid) begin
          wready <= t1_axi_w_transfer(
            TAG_ID,
            wlast,
            wdata,
            wstrobe
          );
        end
      end else begin
        wready <= t1_axi_w_ready(TAG_ID);
      end
    end
  end

  // B channel
  uint8_t bresp_;
  always @(posedge clock) begin
    if (reset) begin
      bvalid <= 1'b0;
      bresp <= 0;
    end
    else begin
      if (bfire) bvalid <= 1'b0;

      if (!bvalid || bfire) begin
        if (t1_axi_b_ready(
          TAG_ID,
          bvalid,
          bresp_,
        )) begin
          bvalid <= 1'b1;
          bresp <= bresp_;
        end
      end
    end
  end

  VerifAxiWrite verif(
    clock,
    reset,
    awfire,
    awlen,
    wfire,
    wlast,
    bfire
  );
endmodule

module VerifAxiRead(
  input   bit         clock,
          bit         reset,
          bit         arfire,
          bit   [7:0] arlen,
          bit         rfire,
          bit         rlast
);
  // check transaction level state machine

  int pending_trans[$];
  int pending_beat;

no_fire_at_reset:
  assert property (@(posedge clock) reset |-> !arfire && !rfire);

no_pending_at_reset:
  assert property (@(posedge clock) reset |-> pending_trans.size() == 0 && pending_beat == 0);

  always @(posedge clock) begin
    if (arfire) begin
      pending_trans.push_back(int'(arlen) + 1);
    end

    if (rfire) begin
      if (pending_beat == 0) begin
        assert_rfire: assert (pending_trans.size() > 0);

        pending_beat = pending_trans.pop_front();
      end

      assert_rlast: assert (rlast == (pending_beat == 1));

      pending_beat -= 1;
    end 
  end
endmodule

module DpiAxiRead #(
  parameter string TAG,
  parameter integer WIDTH
)(
  input   logic         clock,
          logic         reset,

  input   logic         arvalid,
          logic  [31:0] araddr,
          logic   [7:0] arlen,
          logic   [2:0] arsize,
  output  logic         arready,

  output  logic         rvalid,
          logic [WIDTH-1:0] rdata,
          logic   [1:0] rresp,
          logic         rlast,
  input   logic         rready
);
  import t1_common::*;

  if (!(WIDTH inside {8, 16, 32, 64, 128, 256, 512, 1024}))
    $fatal(1, "invalid AXI width");

  // delay to avoid race with init
  axi_tag_t TAG_ID;
  initial #1 TAG_ID = t1_axi_get_id(TAG, WIDTH, "R");

  import "DPI-C" function bit t1_axi_ar_ready(axi_tag_t tag);
  import "DPI-C" function bit t1_axi_ar_transfer(
    axi_tag_t tag,
    uint32_t araddr,
    uint8_t arlen,
    uint8_t arsize
  );
  import "DPI-C" function bit t1_axi_r_valid(
    axi_tag_t tag,
    bit is_transfer,
    output uint8_t rresp,
    output bit rlast,
    output uint8_t[] rdata
  );

  wire arfire = arvalid && arready;
  wire rfire = rvalid && rready;

  // AR channel
  always @(posedge clock) begin
    if (reset) begin
      arready <= 1'b0;
    end
    else begin
      if (arready) begin
        if (arvalid) begin
          arready <= t1_axi_ar_transfer(
            TAG_ID,
            araddr,
            arlen,
            arsize
          );
        end
      end else begin
        arready <= t1_axi_ar_ready(TAG_ID);
      end
    end
  end

  // B channel
  uint8_t rresp_;
  bit rlast_;
  uint8_t rdata_[WIDTH/8];
  always @(posedge clock) begin
    if (reset) begin
      rvalid <= 1'b0;
      rresp <= 0;
      rlast <= 1'b0;
      rdata <= '0;
    end
    else begin
      if (rfire) rvalid <= 1'b0;

      if (!rvalid || rfire) begin
        if (t1_axi_b_ready(
          TAG_ID,
          rvalid,
          rresp_,
          rlast_,
          rdata_
        )) begin
          rvalid <= 1'b1;
          rresp <= rresp_;
          rlast <= rlast_;
          rdata <= rdata_;
        end
      end
    end
  end

  VerifAxiRead verif(
    .clock,
    .reset,
    .arfire,
    .arlen,
    .rfire,
    .rlast
  );
endmodule
