#ifdef COSIM_VERILATOR
#include <VTestBench__Dpi.h>
#endif

#include <csignal>

#include <fmt/core.h>
#include <spdlog/spdlog.h>

#include "exceptions.h"
#include "spdlog-ext.h"
#include "vbridge_impl.h"

static bool terminated = false;

void sigint_handler(int s) {
  terminated = true;
  dpi_finish();
}

#define TRY(action)                                                            \
  try {                                                                        \
    if (!terminated) {                                                         \
      action                                                                   \
    }                                                                          \
  } catch (ReturnException & e) {                                              \
    terminated = true;                                                         \
    Log("SimulationExit")                                                      \
        .info("detect returning instruction, gracefully quit simulation");     \
    /* vbridge_impl_instance.on_exit();      */                                \
    dpi_finish();                                                              \
  } catch (std::runtime_error & e) {                                           \
    terminated = true;                                                         \
    svSetScope(                                                                \
        svGetScopeFromName("TOP.TestBench.verificationModule.dpiError"));      \
    dpi_error(e.what());                                                       \
  }

#if VM_TRACE
void VBridgeImpl::dpiDumpWave() {
  TRY({
    svSetScope(
        svGetScopeFromName("TOP.TestBench.verificationModule.dpiDumpWave"));
    dpi_dump_wave(wave.c_str());
    svSetScope(
        svGetScopeFromName("TOP.TestBench.verificationModule.dpiFinish"));
  })
}
#endif

void VBridgeImpl::timeoutCheck() {
  if (ctx->time() > timeout) {
    Log("TimeoutCheck")
      .with("timeout", timeout)
      .with("time", ctx->time())
      .info("Simulation timeout");
    dpi_finish();
  }
}

[[maybe_unused]] void dpi_init_cosim(svBitVecVal *resetVector) {
  std::signal(SIGINT, sigint_handler);
  auto scope = svGetScopeFromName("TOP.TestBench.verificationModule.dpiFinish");
  CHECK(scope, "Got empty scope");
  svSetScope(scope);
  *reinterpret_cast<uint32_t*>(resetVector) = 0x20000000; //debug
  TRY({
    vbridge_impl_instance.dpiInitCosim(reinterpret_cast<uint32_t*>(resetVector));
  })
}

[[maybe_unused]] void timeout_check() {
  TRY({
    vbridge_impl_instance.timeoutCheck();
  })
}

[[maybe_unused]] void
PeekVectorTL(const svBitVecVal *channel_id, const svBitVecVal *a_opcode,
         const svBitVecVal *a_param, const svBitVecVal *a_size,
         const svBitVecVal *a_source, const svBitVecVal *a_address,
         const svBitVecVal *a_mask, const svBitVecVal *a_data, svBit a_corrupt,
         svBit a_valid, svBit d_ready) {
  TRY({
    uint8_t channel = *reinterpret_cast<const uint8_t *>(channel_id);
    auto peek = VTlInterfacePeek(
      a_opcode, a_param, a_size, a_source, a_address, a_mask, a_data,
      a_corrupt, a_valid, d_ready
    );
    vbridge_impl_instance.PeekVectorTL(channel, peek);
  })
}

[[maybe_unused]] void
PeekMMIOTL(const svBitVecVal *channel_id, const svBitVecVal *a_opcode,
         const svBitVecVal *a_param, const svBitVecVal *a_size,
         const svBitVecVal *a_source, const svBitVecVal *a_address,
         const svBitVecVal *a_mask, const svBitVecVal *a_data, svBit a_corrupt,
         svBit a_valid, svBit d_ready) {
  TRY({
    auto peek = VTlInterfacePeek(
      a_opcode, a_param, a_size, a_source, a_address, a_mask, a_data,
      a_corrupt, a_valid, d_ready
    );
    vbridge_impl_instance.PeekMMIOTL(peek);
  })
}

[[maybe_unused]] void
PeekScarlarTL(const svBitVecVal *channel_id, const svBitVecVal *a_opcode,
         const svBitVecVal *a_param, const svBitVecVal *a_size,
         const svBitVecVal *a_source, const svBitVecVal *a_address,
         const svBitVecVal *a_mask, const svBitVecVal *a_data, svBit a_corrupt,
         svBit a_valid, svBit d_ready) {
  TRY({
    auto peek = VTlInterfacePeek(
      a_opcode, a_param, a_size, a_source, a_address, a_mask, a_data,
      a_corrupt, a_valid, d_ready
    );
    vbridge_impl_instance.PeekScarlarTL(peek);
  })
}

[[maybe_unused]] void PokeVectorTL(const svBitVecVal *channel_id,
                               svBitVecVal *d_opcode, svBitVecVal *d_param,
                               svBitVecVal *d_size, svBitVecVal *d_source,
                               svBit *d_sink, svBit *d_denied,
                               svBitVecVal *d_data, svBit *d_corrupt,
                               svBit *d_valid, svBit *a_ready) {
  TRY({
    uint8_t channel = *reinterpret_cast<const uint8_t *>(channel_id);
    auto poke = VTlInterfacePoke(
      d_opcode, d_param, d_size, d_source, d_sink, d_denied, d_data,
      d_corrupt, d_valid, a_ready
    );
    vbridge_impl_instance.PokeVectorTL(channel, poke);
  })
}

[[maybe_unused]] void PokeScarlarTL(const svBitVecVal *channel_id,
                               svBitVecVal *d_opcode, svBitVecVal *d_param,
                               svBitVecVal *d_size, svBitVecVal *d_source,
                               svBit *d_sink, svBit *d_denied,
                               svBitVecVal *d_data, svBit *d_corrupt,
                               svBit *d_valid, svBit *a_ready) {
  TRY({
    auto poke = VTlInterfacePoke(
      d_opcode, d_param, d_size, d_source, d_sink, d_denied, d_data,
      d_corrupt, d_valid, a_ready
    );
    vbridge_impl_instance.PokeScarlarTL(poke);
  })
}

[[maybe_unused]] void PokeMMIOTL(const svBitVecVal *channel_id,
                               svBitVecVal *d_opcode, svBitVecVal *d_param,
                               svBitVecVal *d_size, svBitVecVal *d_source,
                               svBit *d_sink, svBit *d_denied,
                               svBitVecVal *d_data, svBit *d_corrupt,
                               svBit *d_valid, svBit *a_ready) {
  TRY({
    auto poke = VTlInterfacePoke(
      d_opcode, d_param, d_size, d_source, d_sink, d_denied, d_data,
      d_corrupt, d_valid, a_ready
    );
    vbridge_impl_instance.PokeMMIOTL(poke);
  })
}