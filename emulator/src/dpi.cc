#ifdef COSIM_VERILATOR
#include <VTestBench__Dpi.h>
#endif

#include <csignal>

#include <glog/logging.h>
#include <fmt/core.h>

#include "encoding.h"
#include "svdpi.h"
#include "vbridge_impl.h"
#include "exceptions.h"
#include "perf.h"

static bool terminated = false;

VRFPerf vrf_perf;
ALUPerf alu_perf;
std::vector<LSUPerf> lsu_perfs;
ChainingPerf chaining_perf;

void sigint_handler(int s) {
  terminated = true;
  dpi_finish();
}

void print_perf_summary();

#define TRY(action) \
  try {             \
    if (!terminated) {action}          \
  } catch (ReturnException &e) { \
    terminated = true;                \
    LOG(INFO) << fmt::format("detect returning instruction, gracefully quit simulation");                  \
    print_perf_summary();   \
    dpi_finish();    \
  } catch (std::runtime_error &e) { \
    terminated = true;                \
    LOG(ERROR) << fmt::format("detect exception ({}), gracefully abort simulation", e.what());                 \
    dpi_error(e.what());  \
  }

#if VM_TRACE
void VBridgeImpl::dpiDumpWave() {
  TRY({
    ::dpiDumpWave((wave + ".fst").c_str());
  })
}
#endif

[[maybe_unused]] void dpi_init_cosim() {
  std::signal(SIGINT, sigint_handler);
  auto scope = svGetScopeFromName("TOP.TestBench.verificationModule.dpiFinish");
  CHECK_S(scope);
  svSetScope(scope);
  TRY({
    vbridge_impl_instance.dpiInitCosim();
    lsu_perfs.resize(vbridge_impl_instance.config.tl_bank_number);
  })
}

[[maybe_unused]] void peek_issue(svBit ready, const svBitVecVal *issueIdx) {
  TRY({
    vbridge_impl_instance.dpiPeekIssue(ready, *issueIdx);
  })
}

[[maybe_unused]] void poke_inst(
    svBitVecVal *request_inst,
    svBitVecVal *request_src1Data,
    svBitVecVal *request_src2Data,
    svBit *instValid,

    svBitVecVal *vl,
    svBitVecVal *vStart,
    svBitVecVal *vlmul,
    svBitVecVal *vSew,
    svBitVecVal *vxrm,
    svBit *vta,
    svBit *vma,
    svBit *ignoreException,

    svBit respValid,
    const svBitVecVal *response_data,
    svBit response_vxsat,
    svBit response_rd_valid,
    const svBitVecVal *response_rd_bits,
    svBit response_mem
) {
  TRY({
    vbridge_impl_instance.dpiPokeInst(
        VInstrInterfacePoke{request_inst, request_src1Data, request_src2Data, instValid},
        VCsrInterfacePoke{vl, vStart, vlmul, vSew, vxrm, vta, vma, ignoreException},
        VRespInterface{respValid, *response_data, response_vxsat}
    );
  })
}

[[maybe_unused]] void peek_t_l(
    const svBitVecVal *channel_id,
    const svBitVecVal *a_opcode,
    const svBitVecVal *a_param,
    const svBitVecVal *a_size,
    const svBitVecVal *a_source,
    const svBitVecVal *a_address,
    const svBitVecVal *a_mask,
    const svBitVecVal *a_data,
    svBit a_corrupt,
    svBit a_valid,
    svBit d_ready
) {
  TRY({
    vbridge_impl_instance.dpiPeekTL(
        VTlInterface{*channel_id, *a_opcode, *a_param, *a_size, *a_source, *a_address, a_mask, a_data,
                     a_corrupt, a_valid, d_ready});
    lsu_perfs[*channel_id].peek_tl(a_valid, d_ready);
    lsu_perfs[*channel_id].step();
  })
}

[[maybe_unused]] void poke_t_l(
    const svBitVecVal *channel_id,
    svBitVecVal *d_opcode,
    svBitVecVal *d_param,
    svBitVecVal *d_size,
    svBitVecVal *d_source,
    svBitVecVal *d_sink,
    svBit *d_denied,
    svBitVecVal *d_data,
    svBit *d_corrupt,
    svBit *d_valid,
    svBit *a_ready,
    svBit d_ready
) {
  TRY({
    vbridge_impl_instance.dpiPokeTL(
        VTlInterfacePoke{*channel_id, d_opcode, d_param, d_size, d_source, d_sink, d_denied, d_data,
                         d_corrupt, d_valid, a_ready, d_ready});
    lsu_perfs[*channel_id].poke_tl(*d_valid, *a_ready);
  })
}

[[maybe_unused]] void peek_lsu_enq(const svBitVecVal *enq) {
  TRY({
    vbridge_impl_instance.dpiPeekLsuEnq(VLsuReqEnqPeek{*enq});
  })
}

[[maybe_unused]] void peek_write_queue(
    const svBitVecVal *mshr_index,
    svBit write_valid,
    const svBitVecVal *request_data_vd,
    const svBitVecVal *request_data_offset,
    const svBitVecVal *request_data_mask,
    const svBitVecVal *request_data_data,
    const svBitVecVal *request_data_instIndex,
    const svBitVecVal *request_targetLane
) {
  TRY({
    vbridge_impl_instance.dpiPeekWriteQueue(
        VLsuWriteQueuePeek{*mshr_index, write_valid, *request_data_vd, *request_data_offset,
                           *request_data_mask, *request_data_data, *request_data_instIndex,
                           *request_targetLane});
  })
}

[[maybe_unused]] void peek_vrf_write(
    const svBitVecVal *lane_idx,
    svBit valid,
    const svBitVecVal *request_vd,
    const svBitVecVal *request_offset,
    const svBitVecVal *request_mask,
    const svBitVecVal *request_data,
    const svBitVecVal *request_instIndex
) {
  TRY({
    vbridge_impl_instance.dpiPeekVrfWrite(VrfWritePeek{*lane_idx, valid, *request_vd, *request_offset,
                                                       *request_mask, *request_data, *request_instIndex});
  })
}

[[maybe_unused]] void timeout_check() {
  TRY({
    vbridge_impl_instance.timeoutCheck();
  })
}


[[maybe_unused]] void vrf_monitor(
    const svBitVecVal *lane_idx,
    svBit valid
) TRY({
  vrf_perf.step(*lane_idx, valid);
})

[[maybe_unused]] void alu_monitor(const svBitVecVal *lane_idx,
                                    svBit is_adder_occupied, svBit is_shifter_occupied,
                                    svBit is_multiplier_occupied, svBit is_divider_occupied) TRY({
  alu_perf.step(*lane_idx, is_adder_occupied, is_shifter_occupied, is_multiplier_occupied, is_divider_occupied);
})

[[maybe_unused]] void chaining_monitor(const svBitVecVal *lane_idx, const svBitVecVal *slot_occupied) TRY({
  chaining_perf.step(*lane_idx, slot_occupied);
})

void print_perf_summary() {
  auto output_file_path = get_env_arg_default("PERF_output_file", nullptr);
  if (output_file_path != nullptr) {
    std::ofstream os(output_file_path);

    // each top cycle is 10 cycles for rtl
    os << fmt::format("total_cycles: {}\n", Verilated::threadContextp()->time() / 10);

    vrf_perf.print_summary(os);
    alu_perf.print_summary(os);
    for (int i = 0; i < vbridge_impl_instance.config.tl_bank_number; i++) {
      lsu_perfs[i].print_summary(os, i);
    }
    chaining_perf.print_summary(os);

    LOG(INFO) << fmt::format("perf result saved in '{}'", output_file_path);
  }
}
