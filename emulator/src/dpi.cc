#ifdef COSIM_VERILATOR
#include <VTestBench__Dpi.h>
#endif

#include <csignal>

#include <spdlog/spdlog.h>
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
    Log("SimulationExit") \
      .info("detect returning instruction, gracefully quit simulation"); \
    print_perf_summary();   \
    dpi_finish();    \
  } catch (std::runtime_error &e) { \
    terminated = true;                \
    std::cerr << e.what() << std::endl; \
    Log("RuntimeException") \
      .with("error", e.what()) \
      .warn("detect exception, gracefully abort simulation"); \
    dpi_error(e.what());  \
  }

#if VM_TRACE
void VBridgeImpl::dpiDumpWave() {
  TRY({
    svSetScope(svGetScopeFromName("TOP.TestBench.verificationModule.dpiDumpWave"));
    dpi_dump_wave((wave + ".fst").c_str());
    svSetScope(svGetScopeFromName("TOP.TestBench.verificationModule.dpiFinish"));
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

[[maybe_unused]] void load_unit_monitor(const svBit status_idle,
                                        const svBit status_last,
                                        const svBit tl_port_a_is_valid,
                                        const svBit tl_port_a_is_ready,
                                        const svBit write_ready_for_lsu) TRY({
  Log("LoadUnit")
    .with("status", json {
      { "idle", (bool)status_idle },
      { "last", (bool)status_last }
    })
    .with("tl_port_a", json {
      { "valid", (bool)tl_port_a_is_valid },
      { "ready", (bool)tl_port_a_is_ready }
    })
    .with("write_ready_for_lsu", (bool)write_ready_for_lsu)
    .info();
})

[[maybe_unused]] void load_unit_port_d_monitor(const svBitVecVal *port_d_index,
                                               const svBit port_d_is_valid,
                                               const svBit port_d_is_ready) TRY({
  Log("LoadUnitPortD")
    .with("index", (int)(*port_d_index))
    .with("is_valid", (bool)port_d_is_valid)
    .with("is_ready", (bool)port_d_is_ready)
    .info();
})

[[maybe_unused]] void load_unit_vrf_write_port_monitor(const svBitVecVal *index,
                                                       const svBit is_valid,
                                                       const svBit is_ready) TRY({
  Log("LoadUnitVrfWritePort")
    .with("index", (int)(*index))
    .with("is_valid", (bool)is_valid)
    .with("is_ready", (bool)is_ready)
    .info();
})

[[maybe_unused]] void load_unit_last_cache_line_ack_monitor(const svBitVecVal *index,
                                                            const svBit is_ack) TRY({
  Log("LoadUnitLastCacheLineAck")
    .with("index", (int)(*index))
    .with("is_ack", (bool)is_ack)
    .info();
})

[[maybe_unused]] void load_unit_cache_line_dequeue_monitor(const svBitVecVal *index,
                                                           const svBit is_ready,
                                                           const svBit is_valid) TRY({
  Log("LoadUnitCacheLineDequeueMonitor")
    .with("index", (int)(*index))
    .with("is_ready", (bool)is_ready)
    .with("is_valid", (bool)is_valid)
    .info();
})

[[maybe_unused]] void simple_access_unit_monitor(
  const svBit lsu_request_is_valid,
  const svBit vrf_read_data_port_is_ready,
  const svBit vrf_read_data_port_is_valid,
  const svBit mask_select_is_valid,
  const svBit vrf_write_port_is_ready,
  const svBit vrf_write_port_is_valid,
  const svBitVecVal *current_lane,
  const svBit status_is_offset_group_end,
  const svBit status_is_waiting_first_resp,
  const svBit s0_fire,
  const svBit s1_fire,
  const svBit s2_fire
) TRY({
  Log("SimpleAccessUnit")
    .with("lsu_request_is_valid", (bool)lsu_request_is_valid)
    .with("vrf_read_data_port", json {
      { "is_ready", (bool)vrf_read_data_port_is_ready },
      { "is_valid", (bool)vrf_read_data_port_is_valid }
    })
    .with("vrf_write_port", json {
      { "is_ready", (bool)vrf_write_port_is_ready },
      { "is_valid", (bool)vrf_write_port_is_valid }
    })
    .with("mask_select_is_valid", (bool)mask_select_is_valid)
    .with("status", json {
      { "current_lane", (int)(*current_lane) },
      { "is_waiting_first_response", (bool)status_is_waiting_first_resp },
      { "is_offset_group_end", (bool)status_is_offset_group_end }
    })
    .with("s0_fire", (bool)s0_fire)
    .with("s1_fire", (bool)s1_fire)
    .with("s2_fire", (bool)s2_fire)
    .info();
})

[[maybe_unused]] void simple_access_unit_offset_read_result_monitor(
  const svBitVecVal *index,
  const svBit is_valid
) TRY({
  Log("SimpleAccessUnitOffsetReadResult")
    .with("index", (int)(*index))
    .with("is_valid", (bool)is_valid)
    .info();
})

[[maybe_unused]] void simple_access_unit_indexed_insn_offsets_is_valid_monitor(
  const svBitVecVal *index,
  const svBit is_valid
) TRY({
  Log("SimpleAccessUnitIndexedInsnOffsetIsValid")
    .with("index", (int)(*index))
    .with("is_valid", (bool)is_valid)
    .info();
})

[[maybe_unused]] void store_unit_monitor(
  const svBit vrf_ready_to_store,
  const svBit aligned_dequeue_is_ready,
  const svBit aligned_dequeue_is_valid
) {
  Log("StoreUnitMonitor")
    .with("vrf_ready_to_store", (bool)vrf_ready_to_store)
    .with("aligned_dequeue_is_ready", (bool)aligned_dequeue_is_ready)
    .with("aligned_dequeue_is_valid", (bool)aligned_dequeue_is_valid)
    .info();
}

[[maybe_unused]] void store_unit_tl_port_a_ready_monitor(
  const svBitVecVal *index,
  svLogic ready
) {
  Log("StoreUnitTLPortAReadyMonitor")
    .with("index", (int)(*index))
    .with("is_ready", (bool)ready)
    .info();
}

[[maybe_unused]] void store_unit_tl_port_a_valid_monitor(
  const svBitVecVal *index,
  svLogic valid
) TRY({
  Log("StoreUnitTLPortAReadyMonitor")
    .with("index", (int)(*index))
    .with("is_valid", (bool)valid)
    .info();
})

[[maybe_unused]] void store_unit_vrf_read_data_port_ready_monitor(const svBitVecVal *index, svLogic ready) TRY({
  Log("StoreUnitVrfReadDataPortReadyMonitor")
    .with("index", (int)(*index))
    .with("is_ready", (bool)ready)
    .info();
})

[[maybe_unused]] void store_unit_vrf_read_data_port_valid_monitor(const svBitVecVal *index, svLogic valid) TRY({
  Log("StoreUnitVrfReadDataPortReadyMonitor")
    .with("index", (int)(*index))
    .with("is_valid", (bool)valid)
    .info();
})

[[maybe_unused]] void lane_read_bus_port_monitor(
  const svBitVecVal *index,
  const svBit read_bus_port_enq_ready,
  const svBit read_bus_port_enq_valid,
  const svBit read_bus_port_deq_ready,
  const svBit read_bus_port_deq_valid
) TRY({
  Log("LaneMonitor")
    .with("lane_index", (int)(*index))
    .with("read_bus_port", json {
      { "enq", {
        { "ready", (bool)read_bus_port_enq_ready },
        { "valid", (bool)read_bus_port_enq_valid }
      }},
      { "deq", {
        { "ready", (bool)read_bus_port_deq_ready },
        { "valid", (bool)read_bus_port_deq_valid }
      }}
    })
    .info();
})

[[maybe_unused]] void lane_write_bus_port_monitor(
  const svBitVecVal *index,
  const svBit write_bus_port_enq_ready,
  const svBit write_bus_port_enq_valid,
  const svBit write_bus_port_deq_ready,
  const svBit write_bus_port_deq_valid
) TRY({
  Log("LaneMonitor")
    .with("lane_index", (int)(*index))
    .with("write_bus_port", json {
      { "enq", {
        { "ready", (bool)write_bus_port_enq_ready },
        { "valid", (bool)write_bus_port_enq_valid }
      }},
      { "deq", {
        { "ready", (bool)write_bus_port_deq_ready },
        { "valid", (bool)write_bus_port_deq_valid }
      }}
    })
    .info();
})

[[maybe_unused]] void lane_request_monitor(
  const svBitVecVal *index,
  const svBit lane_request_valid,
  const svBit lane_request_ready
) TRY({
  Log("LaneMonitor")
    .with("lane_index", (int)(*index))
    .with("lane_request", json {
      { "valid", (bool)lane_request_valid },
      { "ready", (bool)lane_request_ready }
    })
    .info();
})

[[maybe_unused]] void lane_response_monitor(
  const svBitVecVal *index,
  const svBit lane_response_valid,
  const svBit lane_response_feedback_valid
) TRY({
  Log("LaneMonitor")
    .with("lane_index", (int)(*index))
    .with("lane_response", json{{ "valid", (bool)lane_response_valid }})
    .with("lane_response_feedback", json{{ "valid", (bool)lane_response_feedback_valid }})
    .info();
})

[[maybe_unused]] void lane_vrf_read_monitor(
  const svBitVecVal *index,
  const svBit vrf_read_address_channel_valid,
  const svBit vrf_read_address_channel_ready
) TRY({
  Log("LaneMonitor")
    .with("lane_index", (int)(*index))
    .with("vrf_read_address_channel", json {
      { "valid", (bool)vrf_read_address_channel_valid },
      { "ready", (bool)vrf_read_address_channel_ready },
     })
    .info();
})

[[maybe_unused]] void lane_vrf_write_monitor(
  const svBitVecVal *index,
  const svBit vrf_write_channel_valid,
  const svBit vrf_write_channel_ready
) TRY({
  Log("LaneMonitor")
    .with("lane_index", (int)(*index))
    .with("vrf_write_channel", json {
      { "valid", (bool)vrf_write_channel_valid },
      { "ready", (bool)vrf_write_channel_valid },
    })
    .info();
})

[[maybe_unused]] void lane_status_monitor(
  const svBitVecVal *index,
  const svBit v0_update_valid,
  const svBit write_ready_for_lsu,
  const svBit vrf_ready_to_store
) TRY({
  Log("LaneMonitor")
    .with("lane_index", (int)(*index))
    .with("v0_update_valid", (bool)v0_update_valid)
    .with("write_ready_for_lsu", (bool)write_ready_for_lsu)
    .with("vrf_ready_to_store", (bool)vrf_ready_to_store)
    .info();
})

[[maybe_unused]] void lane_write_queue_monitor(
  const svBitVecVal *index,
  const svBit write_queue_valid
) TRY({
  Log("LaneMonitor")
    .with("lane_index", (int)(*index))
    .with("write_queue_valid", (bool)write_queue_valid)
    .info();
})

[[maybe_unused]] void lane_read_bus_dequeue_monitor(
  const svBitVecVal *index,
  const svBit read_bus_dequeue_valid
) TRY({
  Log("LaneMonitor")
    .with("lane_index", (int)(*index))
    .with("read_bus_dequeue_valid", (bool)read_bus_dequeue_valid)
    .info();
})

[[maybe_unused]] void cross_lane_monitor(
  const svBitVecVal *index,
  const svBit cross_lane_read_valid,
  const svBit cross_lane_write_valid
) TRY({
  Log("LaneMonitor")
    .with("lane_index", (int)(*index))
    .with("cross_lane", json {
      { "read_valid", (bool)cross_lane_read_valid },
      { "write_valid", (bool)cross_lane_write_valid },
     })
    .info();
})

[[maybe_unused]] void lane_read_bus_data_monitor(
  const svBitVecVal *index,
  const svBit read_bus_data_req_valid
) TRY({
  Log("LaneMonitor")
    .with("lane_index", (int)(*index))
    .with("read_bus_data_req_valid", (bool)read_bus_data_req_valid)
    .info();
})

[[maybe_unused]] void lane_write_bus_data_monitor(
  const svBitVecVal *index,
  const svBit write_bus_data_req_valid
) TRY({
  Log("LaneMonitor")
    .with("lane_index", (int)(*index))
    .with("write_bus_data_req_valid", (bool)write_bus_data_req_valid)
    .info();
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

    Log("PrintPerfSummary")
      .with("path", output_file_path)
      .info("Perf result saved");
  }
}
