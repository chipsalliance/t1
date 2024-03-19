#pragma once

#include <condition_variable>
#include <list>
#include <mutex>
#include <optional>
#include <queue>
#include <thread>
#include <utility>

#ifdef COSIM_VERILATOR
#include <verilated.h>
#include <verilated_cov.h>

#if VM_TRACE
#include <verilated_fst_c.h>
#endif

#include <svdpi.h>
#endif

#include "tilelink.hpp"
#include "tilelink_xbar.hpp"
#include "mmio_mem.hpp"
#include "uartlite.hpp"

struct Config {
  std::string bin_path;
  std::string wave_path;
  uint64_t timeout;
};

class VBridgeImpl {
public:
  VBridgeImpl(const Config cosim_config);
#if VM_TRACE
  void dpiDumpWave();
#endif

  void dpiInitCosim(uint32_t *resetVector);

  void timeoutCheck();

  // tilelink dpi
  void PeekVectorTL(uint8_t channel, VTlInterfacePeek &peek);
  void PeekMMIOTL(VTlInterfacePeek &peek);
  void PeekScarlarTL(VTlInterfacePeek &peek);
  void PokeVectorTL(uint8_t channel, VTlInterfacePoke &poke);
  void PokeMMIOTL(VTlInterfacePoke &poke);
  void PokeScarlarTL(VTlInterfacePoke &poke);

  // Simulator Calls
#ifdef COSIM_VERILATOR
  uint64_t getCycle() { return ctx->time(); }
  void getCoverage() { return ctx->coveragep()->write(); }
#endif

  Config config;

  void on_exit();

private:
  /// file path of executable binary file, which will be executed.
  const std::string bin;

  /// generated waveform path.
  const std::string wave;

  /// RTL timeout cycles
  /// note: this is not the real system cycles, scalar instructions is evaulated
  /// via spike, which is not recorded.
  const uint64_t timeout;

  // uart
  uartlite uart;

  // memory
  /*
    Vector Memory:
    [0-3] 4*512M DDR from 1G step +512M
    [4-11] 8*256K SRAM from 3G step 256K
   */
  mmio_mem vector_mem[12];
  mmio_mem scarlar_mem;

  // tilelink
  tilelink <30, 8, 16, 3> mem_sigs;
  tilelink <29, 8, 2, 3> mmio_sigs;
  tilelink <32, 8, 15, 3> vector_sigs[12];

  tilelink_ref <30, 8, 16, 3> mem_sigs_ref;
  tilelink_ref <29, 8, 2, 3> mmio_sigs_ref;
  tilelink_ref <32, 8, 15, 3> vector_sigs_ref[12];

  tilelink_xbar<30, 8, 16, 3> mem_xbar;
  tilelink_xbar<29, 8, 2, 3> mmio_xbar;
  tilelink_xbar<32, 8, 15, 3> vector_xbar[12];

  // simulator context
#ifdef COSIM_VERILATOR
  VerilatedContext *ctx;

#if VM_TRACE
  VerilatedFstC tfp;
#endif

#endif
};

extern VBridgeImpl vbridge_impl_instance;
