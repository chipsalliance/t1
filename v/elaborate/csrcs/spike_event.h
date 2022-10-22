#pragma once

#include <queue>
#include <optional>

#include "VV.h"
#include "verilated_fst_c.h"
#include "mmu.h"
#include "simple_sim.h"

typedef std::vector<std::tuple<reg_t, uint64_t, uint8_t>> commit_log_mem_t;

class SpikeEvent {
public:
  SpikeEvent(processor_t &proc) : _proc(proc) {};

  void log_reset();

  /// return true if this instruction has been issued.(may not be commited)
  bool get_issued() const;

  /// issue this instruction.
  void issue();

  /// difftest, check all works has been done.
  void reset_issue();

  void commit();

  void reset_commit();

  /// PC
  uint64_t pc() const;

  /// instruction disam
  std::string disam();

  // API poke to simulator for each cycle.
  uint32_t instruction() const;

  uint8_t vsew() const;

  uint8_t vlmul() const;

  bool vma() const;

  bool vta() const;

  bool vill() const;

  uint32_t vl() const;

  uint16_t vstart() const;

  uint8_t vxrm() const;

  bool vxsat() const;

  bool is_load() const;

  bool is_store() const;

  bool need_lsu_index() const;

  uint8_t lsu_index() const;


  void assign_instruction(uint32_t instruction);

  void get_mask();

  void log();

  void set_inst(uint32_t instruction);

  void set_src1(uint32_t src1);

  void set_src2(uint32_t src2);

  void set_vsew(uint8_t vsew);

  void set_vlmul(uint8_t vlmul);

  void set_vma(bool vma);

  void set_vta(bool vta);

  /// todo: void set_vill(uint32_t vsew);
  void set_vl(uint32_t vl);

  void set_vstart(uint16_t vstart);

  void set_vxrm(uint8_t vxrm);

  void set_lsu_index(uint8_t index);

  void set_need_lsu_index();

  void clr_need_lsu_index();

  commit_log_mem_t mem_read_info;

  uint64_t mem_load(uint64_t addr, uint32_t size);

  std::vector<std::tuple<uint64_t, uint64_t, uint8_t>> log_mem_queue;

private:
  bool _need_lsu_index;
  uint8_t _index = 255;
  processor_t &_proc;
  // set when req_ready
  // false: not issued ready
  // true : has been issued
  bool _issue;
  bool _commit;
  bool _load;
  bool _store;
  // instruction(used for driver, this field is always vaild)
  uint64_t _pc;
  uint64_t _mask;
  uint32_t _inst;

  // scalar to vector interface(used for driver)
  uint32_t _src1;
  uint32_t _src2;

  // vector to scalar interface(used for difftest)

  /// vector CSR that used by vector processor
  // vtype
  // 3.4.1
  // [2:0]
  uint8_t _vsew;
  // 3.4.2
  // [2:0]
  uint8_t _vlmul;
  // 3.4.3
  bool _vma;
  // 3.4.3
  bool _vta;
  // 3.4.4
  bool _vill;
  // 3.5
  // [XLEN-1:0]
  // updated with vset{i}vl{i} and fault-only-first vector load instruction variants
  // currently, we don't implement MMU, thus, no fault-only-first will be executed.
  uint32_t _vl;
  // 3.6
  // [XLEN-1:0]
  // 3.7
  // [XLEN-1:0]
  uint16_t _vstart;
  // 3.8
  // [1:0]
  uint8_t _vxrm;
  // 3.9
  bool _vxsat;

  /// pipeline control signal with core
  bool _ignore_exception;
  bool _store_buffer_clear;

  // uint64_t key, uint8_t data <- state->log_reg_write
  std::map<uint64_t, uint8_t> _vrf;

  // load interface, use bool to maintain the state to ganrantee: vector consume load data for exactly one time.
  // if true, this data need to be consumed by RTL, and check the VRF writing event.
  uint64_t _load_base_address;
  std::vector<std::pair<bool, uint32_t>> _load_data;

  // store interface, use bool to maintain the state to ganrantee: vector generate store data for exactly one time.
  // if true, check the memory write event.
  uint64_t _store_base_address;
  std::vector<std::pair<bool, uint32_t>> _store_data;

  void vrf_read(uint8_t index, uint32_t mask);

  void vrf_write(uint8_t index, uint32_t mask);

  void set_csr_signals();

  void memory_store(std::map<uint64_t, uint8_t>);

  void memory_load(std::map<uint64_t, uint8_t>);
};
