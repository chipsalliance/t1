#include "svdpi.h"
#include "VTestHarness__Dpi.h"
#include <cstdio>
#include <cassert>
#include <cstdlib>
#include <csignal>
#include <string>
#include <iostream>
#include "axi4_mem.hpp"
#include "axi4_xbar.hpp"
#include "uartlite.hpp"

#define DPI extern "C"
#define IN const
#define OUT

DPI const char* plus_arg_val(IN char* param);


std::string plusarg_read_str(std::string param) {
    svSetScope(svGetScopeFromName("TOP.TestHarness.dpi_plus_arg"));
    param += "=%s";
    std::string res = std::string(plus_arg_val(param.c_str()));
    std::cout << "plusarg got [" << param << "]=[" << res << "]\n";
    return res;
}

axi4_mem <31,32,4> ram(0x80000000L, true);
axi4     <31,32,4> mem_sigs;
uint32_t entry_addr;

DPI void reset_vector(svBitVecVal* resetVector) {
  *resetVector = entry_addr;
}

axi4     <30,32,4> mmio_sigs;
axi4_xbar<30,32,4> mmio;
uartlite           uart;

DPI void init_cosim() {
    // read plusarg
    std::string trace_file = plusarg_read_str("trace_file");
    std::string init_file = plusarg_read_str("init_file");
    // init dumpwave
    if (trace_file != "") {
        svSetScope(svGetScopeFromName("TOP.TestHarness.dpiDumpWave"));
        dump_wave(trace_file.c_str());
    }
    // sigint signal
    std::signal(SIGINT, [](int) {
        svSetScope(svGetScopeFromName("TOP.TestHarness.dpiFinish"));
        finish();
    });
    // init memory file
    if (init_file != "") {
        ram.load_binary(init_file.c_str());
        entry_addr = ram.get_entry_addr();
        std::cout << "set reset vector to "<< entry_addr << "\n";
    }
    assert(mmio.add_dev(0x10000000, 16, &uart)); // 0x90000000-0xa0000000 mapped to 0x10000000-0x20000000
}

    
extern "C" void AXI4BFMDPI(
    IN  svBitVecVal* arid,
    IN  svBitVecVal* araddr,
    IN  svBitVecVal* arlen,
    IN  svBitVecVal* arsize,
    IN  svBitVecVal* arburst,
    IN  svLogic arvalid,
    OUT svLogic* arready,
    OUT svBitVecVal* rid,
    OUT svBitVecVal* rdata,
    OUT svLogic* rlast,
    OUT svBitVecVal* rresp,
    OUT svLogic* rvalid,
    IN  svLogic rready,
    IN  svBitVecVal* awid,
    IN  svBitVecVal* awaddr,
    IN  svBitVecVal* awlen,
    IN  svBitVecVal* awsize,
    IN  svBitVecVal* awburst,
    IN  svLogic awvalid,
    OUT svLogic* awready,
    IN  svBitVecVal* wdata,
    IN  svLogic wlast,
    IN  svBitVecVal* wstrb,
    IN  svLogic wvalid,
    OUT svLogic* wready,
    OUT svBitVecVal* bid,
    OUT svBitVecVal* bresp,
    OUT svLogic* bvalid,
    IN  svLogic bready) {

    // CTRL START {
    axi4_ref <31,32,4> ref(mem_sigs);
    ram.beat(ref);
    // CTRL  END  }
    
    // output ar
    *arready = mem_sigs.arready;

    // output r
    *rid    = mem_sigs.rid;
    *rdata  = mem_sigs.rdata;
    *rlast  = mem_sigs.rlast;
    *rresp  = mem_sigs.rresp;
    *rvalid = mem_sigs.rvalid;

    // output aw
    *awready= mem_sigs.awready;

    // output w
    *wready = mem_sigs.wready;

    // output b
    *bid    = mem_sigs.bid;
    *bresp  = mem_sigs.bresp;
    *bvalid = mem_sigs.bvalid;

    // input ar
    mem_sigs.arid   = *arid;
    mem_sigs.araddr = *araddr;
    mem_sigs.arlen  = *arlen;
    mem_sigs.arsize = *arsize;
    mem_sigs.arburst= *arburst;
    mem_sigs.arvalid= arvalid;

    // input r
    mem_sigs.rready = rready;

    // input aw
    mem_sigs.awid   = *awid;
    mem_sigs.awaddr = *awaddr;
    mem_sigs.awlen  = *awlen;
    mem_sigs.awsize = *awsize;
    mem_sigs.awburst= *awburst;
    mem_sigs.awvalid= awvalid;

    // input w
    mem_sigs.wdata  = *wdata;
    mem_sigs.wstrb  = *wstrb;
    mem_sigs.wlast  = wlast;
    mem_sigs.wvalid = wvalid;
    
    // input b
    mem_sigs.bready = bready;
}

extern "C" void AXI4MMIODPI(
    IN  svBitVecVal* arid,
    IN  svBitVecVal* araddr,
    IN  svBitVecVal* arlen,
    IN  svBitVecVal* arsize,
    IN  svBitVecVal* arburst,
    IN  svLogic arvalid,
    OUT svLogic* arready,
    OUT svBitVecVal* rid,
    OUT svBitVecVal* rdata,
    OUT svLogic* rlast,
    OUT svBitVecVal* rresp,
    OUT svLogic* rvalid,
    IN  svLogic rready,
    IN  svBitVecVal* awid,
    IN  svBitVecVal* awaddr,
    IN  svBitVecVal* awlen,
    IN  svBitVecVal* awsize,
    IN  svBitVecVal* awburst,
    IN  svLogic awvalid,
    OUT svLogic* awready,
    IN  svBitVecVal* wdata,
    IN  svLogic wlast,
    IN  svBitVecVal* wstrb,
    IN  svLogic wvalid,
    OUT svLogic* wready,
    OUT svBitVecVal* bid,
    OUT svBitVecVal* bresp,
    OUT svLogic* bvalid,
    IN  svLogic bready) {

    // CTRL START {
    axi4_ref <30,32,4> ref(mmio_sigs);
    mmio.beat(ref);

    while (uart.exist_tx()) {
        char c = uart.getc();
        printf("%c",c);
        fflush(stdout);
    }
    // CTRL  END  }
    
    // output ar
    *arready = mmio_sigs.arready;

    // output r
    *rid    = mmio_sigs.rid;
    *rdata  = mmio_sigs.rdata;
    *rlast  = mmio_sigs.rlast;
    *rresp  = mmio_sigs.rresp;
    *rvalid = mmio_sigs.rvalid;

    // output aw
    *awready= mmio_sigs.awready;

    // output w
    *wready = mmio_sigs.wready;

    // output b
    *bid    = mmio_sigs.bid;
    *bresp  = mmio_sigs.bresp;
    *bvalid = mmio_sigs.bvalid;

    // input ar
    mmio_sigs.arid   = *arid;
    mmio_sigs.araddr = *araddr;
    mmio_sigs.arlen  = *arlen;
    mmio_sigs.arsize = *arsize;
    mmio_sigs.arburst= *arburst;
    mmio_sigs.arvalid= arvalid;

    // input r
    mmio_sigs.rready = rready;

    // input aw
    mmio_sigs.awid   = *awid;
    mmio_sigs.awaddr = *awaddr;
    mmio_sigs.awlen  = *awlen;
    mmio_sigs.awsize = *awsize;
    mmio_sigs.awburst= *awburst;
    mmio_sigs.awvalid= awvalid;

    // input w
    mmio_sigs.wdata  = *wdata;
    mmio_sigs.wstrb  = *wstrb;
    mmio_sigs.wlast  = wlast;
    mmio_sigs.wvalid = wvalid;
    
    // input b
    mmio_sigs.bready = bready;
}