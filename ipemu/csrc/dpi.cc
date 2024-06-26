#ifdef COSIM_VERILATOR
#include <VTestBench__Dpi.h>
#endif

#include <csignal>

#include <fmt/core.h>
#include <spdlog/spdlog.h>

#include "encoding.h"
#include "exceptions.h"
#include "svdpi.h"
#include "vbridge_impl.h"

static bool terminated = false;

void sigint_handler(int s) {
  ProgramOutputStoreFile.close();
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
    ProgramOutputStoreFile.close();                                            \
    Log("SimulationExit")                                                      \
        .info("detect returning instruction, gracefully quit simulation");     \
    vbridge_impl_instance.on_exit();                                           \
    dpi_finish();                                                              \
  } catch (std::runtime_error & e) {                                           \
    terminated = true;                                                         \
    ProgramOutputStoreFile.close();                                            \
    svSetScope(                                                                \
        svGetScopeFromName("TOP.TestBench.dpiError"));                         \
    dpi_error(fmt::format("runtime_error occurs: {}", e.what()).c_str());      \
  }

#if VM_TRACE
void VBridgeImpl::dpiDumpWave() {
  TRY({
    svSetScope(
        svGetScopeFromName("TOP.TestBench.dpiDumpWave"));
    dpi_dump_wave(wave.c_str());
    svSetScope(
        svGetScopeFromName("TOP.TestBench.dpiFinish"));
  })
}
#endif

[[maybe_unused]] void dpi_init_cosim() {
  std::signal(SIGINT, sigint_handler);
  auto scope = svGetScopeFromName("TOP.TestBench.dpiFinish");
  CHECK(scope, "Got empty scope");
  svSetScope(scope);
  TRY({
    vbridge_impl_instance.dpiInitCosim();
  })
}

[[maybe_unused]] void peek_issue(svBit ready, const svBitVecVal *issueIdx) {
  TRY({ vbridge_impl_instance.dpiPeekIssue(ready, *issueIdx); })
}

[[maybe_unused]] void
poke_inst(svBitVecVal *request_inst, svBitVecVal *request_src1Data,
          svBitVecVal *request_src2Data, svBit *instValid,

          svBitVecVal *vl, svBitVecVal *vStart, svBitVecVal *vlmul,
          svBitVecVal *vSew, svBitVecVal *vxrm, svBit *vta, svBit *vma,
          svBit *ignoreException,

          svBit respValid, const svBitVecVal *response_data,
          svBit response_vxsat, svBit response_rd_valid,
          const svBitVecVal *response_rd_bits, svBit response_mem) {
  TRY({
    vbridge_impl_instance.dpiPokeInst(
        VInstrInterfacePoke{request_inst, request_src1Data, request_src2Data,
                            instValid},
        VCsrInterfacePoke{vl, vStart, vlmul, vSew, vxrm, vta, vma,
                          ignoreException},
        VRespInterface{respValid, *response_data, response_vxsat});
  })
}

[[maybe_unused]] void
peek_t_l(const svBitVecVal *channel_id, const svBitVecVal *a_opcode,
         const svBitVecVal *a_param, const svBitVecVal *a_size,
         const svBitVecVal *a_source, const svBitVecVal *a_address,
         const svBitVecVal *a_mask, const svBitVecVal *a_data, svBit a_corrupt,
         svBit a_valid, svBit d_ready) {
  TRY({
    vbridge_impl_instance.dpiPeekTL(
        VTlInterface{*channel_id, *a_opcode, *a_param, *a_size, *a_source,
                     *a_address, a_mask, a_data, a_corrupt, a_valid, d_ready});
  })
}

[[maybe_unused]] void poke_t_l(const svBitVecVal *channel_id,
                               svBitVecVal *d_opcode, svBitVecVal *d_param,
                               svBitVecVal *d_size, svBitVecVal *d_source,
                               svBitVecVal *d_sink, svBit *d_denied,
                               svBitVecVal *d_data, svBit *d_corrupt,
                               svBit *d_valid, svBit *a_ready, svBit d_ready) {
  TRY({
    vbridge_impl_instance.dpiPokeTL(VTlInterfacePoke{
        *channel_id, d_opcode, d_param, d_size, d_source, d_sink, d_denied,
        d_data, d_corrupt, d_valid, a_ready, d_ready});
  })
}

[[maybe_unused]] void peek_lsu_enq(const svBitVecVal *enq) {
  TRY({ vbridge_impl_instance.dpiPeekLsuEnq(VLsuReqEnqPeek{*enq}); })
}

[[maybe_unused]] void peek_write_queue(
    const svBitVecVal *mshr_index, svBit write_valid,
    const svBitVecVal *request_data_vd, const svBitVecVal *request_data_offset,
    const svBitVecVal *request_data_mask, const svBitVecVal *request_data_data,
    const svBitVecVal *request_data_instIndex,
    const svBitVecVal *request_targetLane) {
  TRY({
    vbridge_impl_instance.dpiPeekWriteQueue(VLsuWriteQueuePeek{
        *mshr_index, write_valid, *request_data_vd, *request_data_offset,
        *request_data_mask, *request_data_data, *request_data_instIndex,
        *request_targetLane});
  })
}

[[maybe_unused]] void peek_write_queue(const svBitVecVal *mshrIdx, svLogic writeValid,
                                       const svBitVecVal *data_vd, svLogic data_offset,
                                       const svBitVecVal *data_mask, const svBitVecVal *data_data,
                                       const svBitVecVal *data_instructionIndex, const svBitVecVal *targetLane) {
  svBitVecVal offset = (svBitVecVal)data_offset;
  return peek_write_queue(mshrIdx, writeValid, data_vd, &offset, data_mask, data_data, data_instructionIndex, targetLane);
}

[[maybe_unused]] void peek_vrf_write(const svBitVecVal *lane_idx, svBit valid,
                                     const svBitVecVal *request_vd,
                                     const svBitVecVal *request_offset,
                                     const svBitVecVal *request_mask,
                                     const svBitVecVal *request_data,
                                     const svBitVecVal *request_instIndex) {
  TRY({
    vbridge_impl_instance.dpiPeekVrfWrite(
        VrfWritePeek{*lane_idx, valid, *request_vd, *request_offset,
                     *request_mask, *request_data, *request_instIndex});
  })
}

// When offset == 1, verilator might generate DPI function with uint8_t offset
[[maybe_unused]] void peek_vrf_write(const svBitVecVal *laneIdx, svLogic valid,
                                     const svBitVecVal *request_vd,
                                     svLogic request_offset,
                                     const svBitVecVal *request_mask,
                                     const svBitVecVal *request_data,
                                     const svBitVecVal *request_instructionIndex) {
  svBitVecVal offset = (svBitVecVal) request_offset;
  return peek_vrf_write(laneIdx, valid, request_vd, &offset, request_mask, request_data, request_instructionIndex);
}

[[maybe_unused]] void timeout_check() {
  TRY({ vbridge_impl_instance.timeoutCheck(); })
}
