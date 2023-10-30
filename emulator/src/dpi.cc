#ifdef COSIM_VERILATOR
#include <VTestBench__Dpi.h>
#endif

#include <csignal>

#include <fmt/core.h>
#include <spdlog/spdlog.h>

#include "encoding.h"
#include "exceptions.h"
#include "perf.h"
#include "svdpi.h"
#include "vbridge_impl.h"

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

#define TRY(action)                                                            \
  try {                                                                        \
    if (!terminated) {                                                         \
      action                                                                   \
    }                                                                          \
  } catch (ReturnException & e) {                                              \
    terminated = true;                                                         \
    Log("SimulationExit")                                                      \
        .info("detect returning instruction, gracefully quit simulation");     \
    print_perf_summary();                                                      \
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
    dpi_dump_wave((wave + ".fst").c_str());
    svSetScope(
        svGetScopeFromName("TOP.TestBench.verificationModule.dpiFinish"));
  })
}
#endif

[[maybe_unused]] void dpi_init_cosim() {
  std::signal(SIGINT, sigint_handler);
  auto scope = svGetScopeFromName("TOP.TestBench.verificationModule.dpiFinish");
  CHECK(scope, "Got empty scope");
  svSetScope(scope);
  TRY({
    vbridge_impl_instance.dpiInitCosim();
    lsu_perfs.resize(vbridge_impl_instance.config.tl_bank_number);
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
    lsu_perfs[*channel_id].peek_tl(a_valid, d_ready);
    lsu_perfs[*channel_id].step();
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
    lsu_perfs[*channel_id].poke_tl(*d_valid, *a_ready);
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

[[maybe_unused]] void timeout_check() {
  TRY({ vbridge_impl_instance.timeoutCheck(); })
}

[[maybe_unused]] void vrf_monitor(const svBitVecVal *lane_idx, svBit valid) {
  TRY({ vrf_perf.step(*lane_idx, valid); })
}

[[maybe_unused]] void alu_monitor(const svBitVecVal *lane_idx,
                                  svBit is_adder_occupied,
                                  svBit is_shifter_occupied,
                                  svBit is_multiplier_occupied,
                                  svBit is_divider_occupied) {
  TRY({
    alu_perf.step(*lane_idx, is_adder_occupied, is_shifter_occupied,
                  is_multiplier_occupied, is_divider_occupied);
  })
}

[[maybe_unused]] void chaining_monitor(const svBitVecVal *lane_idx,
                                       const svBitVecVal *slot_occupied) {
  TRY({ chaining_perf.step(*lane_idx, slot_occupied); })
}

void load_unit_monitor(
    svLogic LSURequestValid, svLogic idle, svLogic tlPortAIsValid,
    svLogic tlPortAIsReady, svLogic addressConflict, svLogic tlPortDIsValid0,
    svLogic tlPortDIsValid1, svLogic tlPortDIsReady0, svLogic tlPortDIsReady1,
    svLogic queueValid0, svLogic queueValid1, svLogic queueReady0,
    svLogic queueReady1, svLogic cacheLineDequeueValid0,
    svLogic cacheLineDequeueValid1, svLogic cacheLineDequeueReady0,
    svLogic cacheLineDequeueReady1, svLogic unalignedCacheLine,
    svLogic alignedDequeueReady, svLogic alignedDequeueValid,
    svLogic LoadUnitWriteReadyForLSU, svLogic vrfWritePortValid0,
    svLogic vrfWritePortValid1, svLogic vrfWritePortValid2,
    svLogic vrfWritePortValid3, svLogic vrfWritePortValid4,
    svLogic vrfWritePortValid5, svLogic vrfWritePortValid6,
    svLogic vrfWritePortValid7, svLogic vrfWritePortReady0,
    svLogic vrfWritePortReady1, svLogic vrfWritePortReady2,
    svLogic vrfWritePortReady3, svLogic vrfWritePortReady4,
    svLogic vrfWritePortReady5, svLogic vrfWritePortReady6,
    svLogic vrfWritePortReady7) {
  TRY({
    Log("LoadUnit")
        .with("lsu_request_is_valid", (bool)LSURequestValid)
        .with("idle", (bool)idle)
        .with("address_conlict", (bool)addressConflict)
        .with("tl_port_a",
              json{
                  {"valid", (bool)tlPortAIsValid},
                  {"ready", (bool)tlPortAIsReady},
              })
        .with("tl_port_d", std::vector{json{{"ready", (bool)tlPortDIsReady0},
                                            {"valid", (bool)tlPortDIsValid0}},
                                       json{{"ready", (bool)tlPortDIsReady1},
                                            {"valid", (bool)tlPortDIsValid1}}})
        .with("queue", std::vector{json{{"ready", (bool)queueReady0},
                                        {"valid", (bool)queueValid0}},
                                   json{{"ready", (bool)queueReady1},
                                        {"valid", (bool)queueValid1}}})
        .with("cacheLineDequeue",
              std::vector{json{{"ready", (bool)cacheLineDequeueReady0},
                               {"valid", (bool)cacheLineDequeueValid0}},
                          json{{"ready", (bool)cacheLineDequeueReady1},
                               {"valid", (bool)cacheLineDequeueValid1}}})
        .with("alignedDequeue", json{{"ready", (bool)alignedDequeueReady},
                                     {"valid", (bool)alignedDequeueValid}})
        .with("writeReadyForLSU", (bool)LoadUnitWriteReadyForLSU)
        .with("vrfWritePort",
              std::vector{
                  json{{"ready", (bool)vrfWritePortReady0},
                       {"valid", (bool)vrfWritePortValid0}},
                  json{{"ready", (bool)vrfWritePortReady1},
                       {"valid", (bool)vrfWritePortValid1}},
                  json{{"ready", (bool)vrfWritePortReady2},
                       {"valid", (bool)vrfWritePortValid2}},
                  json{{"ready", (bool)vrfWritePortReady3},
                       {"valid", (bool)vrfWritePortValid3}},
                  json{{"ready", (bool)vrfWritePortReady4},
                       {"valid", (bool)vrfWritePortValid4}},
                  json{{"ready", (bool)vrfWritePortReady5},
                       {"valid", (bool)vrfWritePortValid5}},
                  json{{"ready", (bool)vrfWritePortReady6},
                       {"valid", (bool)vrfWritePortValid6}},
                  json{{"ready", (bool)vrfWritePortReady7},
                       {"valid", (bool)vrfWritePortValid7}},
              })
        .info();
  })
}

void store_unit_monitor(
    svLogic idle, svLogic lsuRequestIsValid, svLogic tlPortAIsValid0,
    svLogic tlPortAIsValid1, svLogic tlPortAIsReady0, svLogic tlPortAIsReady1,
    svLogic addressConflict, svLogic vrfReadDataPortIsValid0,
    svLogic vrfReadDataPortIsValid1, svLogic vrfReadDataPortIsValid2,
    svLogic vrfReadDataPortIsValid3, svLogic vrfReadDataPortIsValid4,
    svLogic vrfReadDataPortIsValid5, svLogic vrfReadDataPortIsValid6,
    svLogic vrfReadDataPortIsValid7, svLogic vrfReadDataPortIsReady0,
    svLogic vrfReadDataPortIsReady1, svLogic vrfReadDataPortIsReady2,
    svLogic vrfReadDataPortIsReady3, svLogic vrfReadDataPortIsReady4,
    svLogic vrfReadDataPortIsReady5, svLogic vrfReadDataPortIsReady6,
    svLogic vrfReadDataPortIsReady7, svLogic vrfReadyToStore,
    svLogic alignedDequeueReady, svLogic alignedDequeueValid) {
  TRY({
    Log("StoreUnit")
        .with("idle", (bool)idle)
        .with("lsu_request_is_valid", (bool)lsuRequestIsValid)
        .with("tl_port_a", std::vector{json{
                                           {"valid", (bool)tlPortAIsValid0},
                                           {"ready", (bool)tlPortAIsReady0},
                                       },
                                       json{
                                           {"valid", (bool)tlPortAIsValid1},
                                           {"ready", (bool)tlPortAIsReady1},
                                       }})
        .with("address_conflict", (bool)addressConflict)
        .with("vrfReadDataPort",
              std::vector{
                  json{{"ready", (bool)vrfReadDataPortIsReady0},
                       {"valid", (bool)vrfReadDataPortIsValid0}},
                  json{{"ready", (bool)vrfReadDataPortIsReady1},
                       {"valid", (bool)vrfReadDataPortIsValid1}},
                  json{{"ready", (bool)vrfReadDataPortIsReady2},
                       {"valid", (bool)vrfReadDataPortIsValid2}},
                  json{{"ready", (bool)vrfReadDataPortIsReady3},
                       {"valid", (bool)vrfReadDataPortIsValid3}},
                  json{{"ready", (bool)vrfReadDataPortIsReady4},
                       {"valid", (bool)vrfReadDataPortIsValid4}},
                  json{{"ready", (bool)vrfReadDataPortIsReady5},
                       {"valid", (bool)vrfReadDataPortIsValid5}},
                  json{{"ready", (bool)vrfReadDataPortIsReady6},
                       {"valid", (bool)vrfReadDataPortIsValid6}},
                  json{{"ready", (bool)vrfReadDataPortIsReady7},
                       {"valid", (bool)vrfReadDataPortIsValid7}},
              })
        .with("alignedDequeue", json{{"ready", (bool)alignedDequeueReady},
                                     {"valid", (bool)alignedDequeueValid}})
        .info();
  })
}

void v_monitor(svLogic requestValid, svLogic requestReady,
               svLogic requestRegValid, svLogic requestRegDequeueValid,
               svLogic requestRegDequeueReady, svLogic executionReady,
               svLogic slotReady, svLogic waitForGather,
               svLogic instructionRawReady, svLogic responseValid,
               svLogic sMaskUnitExecuted0, svLogic sMaskUnitExecuted1,
               svLogic sMaskUnitExecuted2, svLogic sMaskUnitExecuted3,
               svLogic wLast0, svLogic wLast1, svLogic wLast2, svLogic wLast3,
               svLogic isLastInst0, svLogic isLastInst1, svLogic isLastInst2,
               svLogic isLastInst3) {
  TRY({
    Log("V")
        .with("request",
              json{
                  {"valid", (bool)requestValid},
                  {"ready", (bool)requestReady},
              })
        .with("request_reg_valid", (bool)requestRegValid)
        .with("request_reg_dequeue",
              json{
                  {"valid", (bool)requestRegDequeueValid},
                  {"ready", (bool)requestRegDequeueReady},
              })
        .with("execution_ready", (bool)executionReady)
        .with("slot_ready", (bool)slotReady)
        .with("wait_for_gather", (bool)waitForGather)
        .with("instrution_RAW_ready", (bool)instructionRawReady)
        .with("response_valid", (bool)responseValid)
        .with("slots",
              std::vector{
                  json{{"s_mask_unit_exectued", (bool)sMaskUnitExecuted0},
                       {"w_last", (bool)wLast0},
                       {"is_last_instruction", (bool)isLastInst0}},
                  json{{"s_mask_unit_exectued", (bool)sMaskUnitExecuted1},
                       {"w_last", (bool)wLast1},
                       {"is_last_instruction", (bool)isLastInst1}},
                  json{{"s_mask_unit_exectued", (bool)sMaskUnitExecuted2},
                       {"w_last", (bool)wLast2},
                       {"is_last_instruction", (bool)isLastInst2}},
                  json{{"s_mask_unit_exectued", (bool)sMaskUnitExecuted3},
                       {"w_last", (bool)wLast3},
                       {"is_last_instruction", (bool)isLastInst3}},
              })
        .info();
  });
}

void other_unit_monitor(svLogic lsuRequestIsValid, svLogic s0EnqueueValid,
                        svLogic stateIsRequest, svLogic maskCheck,
                        svLogic indexCheck, svLogic fofCheck, svLogic s0Fire,
                        svLogic s1Fire, svLogic s2Fire, svLogic tlPortAIsReady,
                        svLogic tlPortAIsValid, svLogic s1Valid,
                        svLogic sourceFree, svLogic tlPortDIsValid,
                        svLogic tlPortDIsReady, svLogic VrfWritePortIsReady,
                        svLogic VrfWritePortIsValid,
                        const svBitVecVal *stateValue) {
  TRY({
    Log("OtherUnit")
        .with("lsu_request_is_valid", (bool)lsuRequestIsValid)
        .with("s0_enqueue_valid", (bool)s0EnqueueValid)
        .with("state_is_request", (bool)stateIsRequest)
        .with("mask_check", (bool)maskCheck)
        .with("index_check", (bool)indexCheck)
        .with("fof_check", (bool)fofCheck)
        .with("s0_fire", (bool)s0Fire)
        .with("s1_fire", (bool)s1Fire)
        .with("s2_fire", (bool)s2Fire)
        .with("tl_port_a", json{{"valid", (bool)tlPortAIsValid},
                                {"ready", (bool)tlPortAIsReady}})
        .with("s1_valid", (bool)s1Valid)
        .with("source_free", (bool)sourceFree)
        .with("tl_port_d", json{{"valid", (bool)tlPortDIsValid},
                                {"ready", (bool)tlPortDIsReady}})
        .with("vrf_write_port", json{{"valid", (bool)VrfWritePortIsValid},
                                     {"ready", (bool)VrfWritePortIsReady}})
        .info();
  })
}

void lane_monitor(const svBitVecVal *index, svLogic laneRequestValid,
                  svLogic laneRequestReady, svLogic lastSlotOccupied,
                  svLogic vrfInstructionWriteReportReady, svLogic slotOccupied0,
                  svLogic slotOccupied1, svLogic slotOccupied2,
                  svLogic slotOccupied3,
                  const svBitVecVal *instructionFinished) {
  TRY({
    Log("Lane")
        .with("index", (int)(*index))
        .with("lane_request", json{{"valid", (bool)laneRequestValid},
                                   {"ready", (bool)laneRequestReady}})
        .with("last_slot_occpied", (bool)lastSlotOccupied)
        .with("vrf_instruction_write_report_ready",
              (bool)vrfInstructionWriteReportReady)
        .with("slot_occpied",
              std::vector{
                  (bool)slotOccupied0,
                  (bool)slotOccupied1,
                  (bool)slotOccupied2,
                  (bool)slotOccupied3,
              })
        .with("instruction_finished", (int)(*instructionFinished))
        .info();
  })
}

void lane_slot_monitor(
    const svBitVecVal *laneIndex, const svBitVecVal *slotIndex,
    svLogic stage0EnqueueReady, svLogic stage0EnqueueValid,
    svLogic changingMaskSet, svLogic slotActive, svLogic slotOccupied,
    svLogic pipeFinish, svLogic stage1DequeueReady, svLogic stage1DequeueValid,
    svLogic stage1HasDataOccpied, svLogic stage1Finishing,
    svLogic stage1VrfReadReadyRequest0, svLogic stage1VrfReadReadyRequest1,
    svLogic stage1VrfReadReadyRequest2, svLogic stage1VrfReadValidRequest0,
    svLogic stage1VrfReadValidRequest1, svLogic stage1VrfReadValidRequest2,
    svLogic executionUnitVfuRequestReady, svLogic executionUnitVfuRequestValid,
    svLogic stage3VrfWriteReady, svLogic stage3VrfWriteValid) {
  TRY({
    Log("Lane")
        .with("index", (int)(*laneIndex))
        .with("slot_index", (int)(*slotIndex))
        .with("stage_0_enqueue", json{{"valid", (bool)stage0EnqueueValid},
                                      {"ready", (bool)stage0EnqueueReady}})
        .with("changing_mask_set", (bool)(changingMaskSet))
        .with("slot_active", (bool)(slotActive))
        .with("slot_occupied", (bool)(slotOccupied))
        .with("pipe_finish", (bool)(pipeFinish))
        .with("stage_1",
              json{{"dequeue",
                    {"valid", (bool)stage1DequeueValid},
                    {"ready", (bool)stage1DequeueReady}},
                   {"has_data_occupied", (bool)stage1HasDataOccpied},
                   {"finishing", (bool)stage1Finishing},
                   {"VRF_read_request",
                    std::vector{
                        json{{"ready", (bool)stage1VrfReadReadyRequest0},
                             {"valid", (bool)stage1VrfReadValidRequest0}},
                        json{{"ready", (bool)stage1VrfReadReadyRequest1},
                             {"valid", (bool)stage1VrfReadValidRequest1}},
                        json{{"ready", (bool)stage1VrfReadReadyRequest2},
                             {"valid", (bool)stage1VrfReadValidRequest2}},
                    }}})
        .with("stage_3_vrf_write", json{{"valid", (bool)stage3VrfWriteValid},
                                        {"ready", (bool)stage3VrfWriteReady}})
        .info();
  })
}

void lane_last_slot_monitor(
    const svBitVecVal *laneIndex, const svBitVecVal *slotIndex,
    svLogic stage0EnqueueReady, svLogic stage0EnqueueValid,
    svLogic changingMaskSet, svLogic slotActive, svLogic slotOccupied,
    svLogic pipeFinish, svLogic stage1DequeueReady, svLogic stage1DequeueValid,
    svLogic stage1HasDataOccpied, svLogic stage1Finishing,
    svLogic stage1VrfReadReadyRequest0, svLogic stage1VrfReadReadyRequest1,
    svLogic stage1VrfReadReadyRequest2, svLogic stage1VrfReadValidRequest0,
    svLogic stage1VrfReadValidRequest1, svLogic stage1VrfReadValidRequest2,
    svLogic executionUnitVfuRequestReady, svLogic executionUnitVfuRequestValid,
    svLogic stage3VrfWriteReady, svLogic stage3VrfWriteValid,
    svLogic slotShiftValid, svLogic decodeResultIsCrossReadOrWrite,
    svLogic decodeResultIsScheduler, svLogic stage1ReadFinish,
    svLogic sSendCrossReadResultLSB, svLogic sSendCrossReadResultMSB,
    svLogic wCrossReadLSB, svLogic wCrossReadMSB) {
  TRY({
    Log("Lane")
        .with("index", (int)(*laneIndex))
        .with("slot_index", (int)(*slotIndex))
        .with("stage_0_enqueue", json{{"valid", (bool)stage0EnqueueValid},
                                      {"ready", (bool)stage0EnqueueReady}})
        .with("changing_mask_set", (bool)(changingMaskSet))
        .with("slot_active", (bool)(slotActive))
        .with("slot_occupied", (bool)(slotOccupied))
        .with("pipe_finish", (bool)(pipeFinish))
        .with("stage_1",
              json{{"dequeue",
                    {"valid", (bool)stage1DequeueValid},
                    {"ready", (bool)stage1DequeueReady}},
                   {"has_data_occupied", (bool)stage1HasDataOccpied},
                   {"finishing", (bool)stage1Finishing},
                   {"read_finish", (bool)stage1ReadFinish},
                   {"sSendCrossReadResultLSB", (bool)sSendCrossReadResultLSB},
                   {"sSendCrossReadResultMSB", (bool)sSendCrossReadResultMSB},
                   {"wCrossReadLSB", (bool)wCrossReadLSB},
                   {"wCrossReadMSB", (bool)wCrossReadMSB},
                   {"VRF_read_request",
                    std::vector{
                        json{{"ready", (bool)stage1VrfReadReadyRequest0},
                             {"valid", (bool)stage1VrfReadValidRequest0}},
                        json{{"ready", (bool)stage1VrfReadReadyRequest1},
                             {"valid", (bool)stage1VrfReadValidRequest1}},
                        json{{"ready", (bool)stage1VrfReadReadyRequest2},
                             {"valid", (bool)stage1VrfReadValidRequest2}},
                    }}})
        .with("stage_3_vrf_write", json{{"valid", (bool)stage3VrfWriteValid},
                                        {"ready", (bool)stage3VrfWriteReady}})
        .with("slot_shift_valid", (bool)slotShiftValid)
        .with("decode_result",
              json{{"is_cross_read_or_write",
                    (bool)decodeResultIsCrossReadOrWrite},
                   {"is_scheduler", (bool)decodeResultIsScheduler}})
        .info();
  })
}

void print_perf_summary() {
  auto output_file_path = get_env_arg_default("PERF_output_file", nullptr);
  if (output_file_path != nullptr) {
    std::ofstream os(output_file_path);

    // each top cycle is 10 cycles for rtl
    os << fmt::format("total_cycles: {}\n",
                      Verilated::threadContextp()->time() / 10);

    vrf_perf.print_summary(os);
    alu_perf.print_summary(os);
    for (int i = 0; i < vbridge_impl_instance.config.tl_bank_number; i++) {
      lsu_perfs[i].print_summary(os, i);
    }
    chaining_perf.print_summary(os);

    Log("PrintPerfSummary")
        .with("path", output_file_path)
        .info("Perf result saved");
  }
}
