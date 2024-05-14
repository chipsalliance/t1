#pragma once

#include <condition_variable>
#include <list>
#include <mutex>
#include <optional>
#include <queue>
#include <thread>
#include <utility>

#include <mmu.h>

#ifdef COSIM_VERILATOR
  #include <verilated.h>
  #include <verilated_cov.h>

  #if VM_TRACE
    #include <verilated_fst_c.h>
  #endif
#endif

#include <dramsim3.h>

#include "encoding.h"
#include "simple_sim.h"
#include "spike_event.h"
#include "util.h"

class SpikeEvent;

struct Config {
  std::string bin_path;
  std::string wave_path;
  std::optional<std::string> perf_path;

  uint64_t timeout;
#ifdef VM_TRACE
  uint64_t dump_from_cycle;
#endif

  double tck;
  std::optional<std::string> dramsim3_config_path;
  std::optional<std::string> dramsim3_result_dir;
  // TODO: move these configs to compiler time after t1 support OM:
  // TODO: these are unused parameters
  size_t vlen;
  size_t dlen;
  size_t tl_bank_number;

  size_t datapath_width;
  size_t lane_number;
  size_t elen;
  size_t vreg_number;
  size_t mshr_number;
  size_t lsu_idx_default;
  size_t vlen_in_bytes;
  size_t datapath_width_in_bytes;
};

struct TLReqRecord {
  std::vector<uint8_t> data;
  size_t t;
  size_t size_by_byte;
  reg_t addr;
  uint16_t source;

  bool muxin_read_required; // Read required for partial writes
  bool muxin_read_sent = false; // Mux-in read consists of at most one transaction

  const SpikeEvent *se;

  // For writes, as soon as the transaction is sent to the controller, the request is resolved, so we don't have to track the number
  //   of bytes that have been processed by the memory controller

  // Only meaningful for writes, this is the number of bytes written by user.
  size_t bytes_received = 0;

  // This is the number of bytes(or worth-of transaction for reads) sent to the memory controller
  size_t bytes_committed = 0;

  // This is the number of bytes that have been processed by the memory controller
  size_t bytes_processed = 0;

  // For read, number of bytes returned to user
  size_t bytes_returned = 0;

  /// when opType set to nil, it means this record is already sent back
  enum class opType { Nil, Get, PutFullData } op;

  TLReqRecord(const SpikeEvent *se, size_t t, std::vector<uint8_t> data, size_t size_by_byte,
              reg_t addr, uint16_t source, opType op, reg_t burst_size)
      : se(se), t(t), data(std::move(data)), size_by_byte(size_by_byte), addr(addr),
        source(source), op(op) {
          muxin_read_required = op == opType::PutFullData && size_by_byte < burst_size;
        };
  
  reg_t aligned_addr(reg_t burst_size) const {
    return (addr / burst_size) * burst_size;
  }

  [[nodiscard]] bool done_commit() const {
    if(muxin_read_required) return false;
    if(bytes_committed < size_by_byte) return false;
    return true;
  }

  [[nodiscard]] bool done_return() const {
    if(muxin_read_required) return false;
    if(op == opType::PutFullData) return bytes_returned > 0;
    else return bytes_returned >= size_by_byte;
  }

  [[nodiscard]] bool fully_done() const {
    if(!done_return()) return false;
    if(op == opType::PutFullData && bytes_processed < size_by_byte) return false;
    return true;
  }

  [[nodiscard]] std::optional<std::pair<reg_t, bool>> issue_mem_request(reg_t burst_size) const {
    if(muxin_read_required) {
      if(muxin_read_sent) return {};
      return {{ aligned_addr(burst_size), false }};
    }

    if(bytes_committed >= size_by_byte) return {};
    if(op == opType::PutFullData) {
      if(bytes_committed + std::min(burst_size, size_by_byte) > bytes_received) return {};
    }

    return {{ aligned_addr(burst_size) + bytes_committed, op == opType::PutFullData }};
  }

  void commit_mem_request(reg_t burst_bytes) {
    if(muxin_read_required) muxin_read_sent = true;
    else bytes_committed += burst_bytes;
  }

  void skip() {
    this->muxin_read_required = false;
    if(op == opType::PutFullData)
      this->bytes_committed = bytes_received;
    else
      this->bytes_committed = size_by_byte;
    this->bytes_processed = this->bytes_committed;
  }

  bool resolve_mem_response(reg_t resp_addr, reg_t burst_bytes) {
    if(muxin_read_required) {
      if(aligned_addr(burst_bytes) != resp_addr) return false;
      muxin_read_required = false; // Resolve mux-in read
      return true;
    } else {
      if(bytes_processed >= size_by_byte || aligned_addr(burst_bytes) + bytes_processed != resp_addr) return false;
      bytes_processed += burst_bytes;
      return true;
    }
  }

  // Returns: offset!
  [[nodiscard]] std::optional<std::pair<reg_t, size_t>> issue_tl_response(size_t tl_bytes) const {
    if(op == opType::PutFullData) {
      // Writes need to wait for commit finishes
      if(!done_commit()) return {};
      return {{ 0, 0 }}; // Write resolves all together
    } else {
      // Reads need to wait for available data
      auto transfer_size = std::min(tl_bytes, size_by_byte);
      if(bytes_returned - bytes_processed < transfer_size) return {};
      return {{ bytes_returned, transfer_size }};
    }
  }

  void commit_tl_respones(reg_t tl_bytes) {
    bytes_returned += tl_bytes;
  }

  void format() const {
    std::cout<<(op == opType::Get ? "R" : "W")<<std::endl;
    std::cout<<"- size: "<<size_by_byte<<std::endl;
    if(op == opType::PutFullData) std::cout<<"- received: "<<bytes_received<<std::endl;
    std::cout<<"- committed: "<<bytes_committed<<std::endl;
    std::cout<<"- processed: "<<bytes_processed<<std::endl;
    std::cout<<"- returned: "<<bytes_returned<<std::endl;
  }
};

class VBridgeImpl {
public:
  explicit VBridgeImpl(Config cosim_config);
#if VM_TRACE
  void dpiDumpWave();
#endif

  void dpiInitCosim();

  void timeoutCheck();

  uint8_t load(uint64_t address);

  // Simulator Calls
#ifdef COSIM_VERILATOR
  uint64_t get_t();
  void getCoverage();
#endif

  void dpiPokeInst(const VInstrInterfacePoke &v_instr,
                   const VCsrInterfacePoke &v_csr,
                   const VRespInterface &v_resp);
  void dpiPokeTL(const VTlInterfacePoke &v_tl_poke);
  void dpiPeekTL(const VTlInterface &v_tl);
  void dpiPeekWriteQueue(const VLsuWriteQueuePeek &v_enq);
  void dpiPeekIssue(svBit ready, svBitVecVal issueIdx);
  void dpiPeekLsuEnq(const VLsuReqEnqPeek &lsu_req_enq);
  void dpiPeekVrfWrite(const VrfWritePeek &v_enq);

  Config config;

  void on_exit();

private:
  std::string varch;
  cfg_t cfg;
  simple_sim sim;
  isa_parser_t isa;
  processor_t proc;
  std::vector<std::multimap<size_t, TLReqRecord>>
      tl_req_record_of_bank; // indexed by get_t()
  std::vector<std::optional<size_t>>
      tl_req_waiting_ready; // the get_t() of a req response waiting for ready
  std::vector<std::optional<size_t>>
      tl_req_ongoing_burst; // the get_t() of a req with ongoing burst

  SpikeEvent *se_to_issue;

  /// to rtl stack
  /// in the spike thread, spike should detech if this queue is full, if not
  /// full, execute until a vector instruction, record the behavior of this
  /// instruction, and send to str_stack. in the RTL thread, the RTL driver will
  /// consume from this queue, drive signal based on the queue. size of this
  /// queue should be as big as enough to make rtl free to run, reducing the
  /// context switch overhead.
  std::list<SpikeEvent> to_rtl_queue;

  // in vrf_shadow we keep a duplicate of vrf in rtl, in order to detect
  // unexpected vrf write
  std::unique_ptr<uint8_t[]> vrf_shadow;

  /// file path of executable binary file, which will be executed.
  const std::string bin;

  /// generated waveform path.
  const std::string wave;

  /// generated perf report path.
  /// TODO: remove it?
  const std::optional<std::string> perf_path;

  /// RTL timeout cycles
  /// note: this is not the real system cycles, scalar instructions is evaulated
  /// via spike, which is not recorded.
  const uint64_t timeout;
#ifdef VM_TRACE
  const uint64_t dump_from_cycle;
#endif

  int64_t spike_cycles = 0;

  struct CallFrame {
    std::string func_name;
    reg_t func_addr;
    reg_t return_addr;
    int64_t spike_cycle;
  };
  std::vector<CallFrame> frames;
  friend class ConsoleSink;

  std::optional<SpikeEvent> create_spike_event(insn_fetch_t fetch);

  std::optional<SpikeEvent> spike_step();
  SpikeEvent *find_se_to_issue();

  // for load instructions, rtl would commit after vrf write requests enters the
  // write queue (but not actually written), hence for these instructions, we
  // should record vrf write on queue, and ignore them on vrf.write
  void record_rf_queue_accesses(const VLsuWriteQueuePeek &lsu_queues);
  void record_rf_accesses(const VrfWritePeek &rf_writs);
  void receive_tl_d_ready(const VTlInterface &tl);
  void return_tl_response(const VTlInterfacePoke &tl_poke);
  void receive_tl_req(const VTlInterface &tl);
  void update_lsu_idx(const VLsuReqEnqPeek &enq);

  void add_rtl_write(SpikeEvent *se, uint32_t lane_idx, uint32_t vd,
                     uint32_t offset, uint32_t mask, uint32_t data,
                     uint32_t idx);

  // simulator context
#ifdef COSIM_VERILATOR
  VerilatedContext *ctx;

#if VM_TRACE
  VerilatedFstC tfp;
#endif

#endif

  void dramsim_drive(uint32_t channel_id);
  void dramsim_resolve(uint32_t channel_id, reg_t addr);
  size_t dramsim_burst_size(uint32_t channel_id) const;
  std::vector<std::pair<dramsim3::MemorySystem, uint64_t>> drams; // (controller, dram tick)
  bool using_dramsim3 = false;

  double tck;
  uint64_t last_commit_time = 0;
  bool dump_start = false;
};

extern VBridgeImpl vbridge_impl_instance;
