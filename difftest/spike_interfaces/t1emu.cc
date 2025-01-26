#include <type_traits>
#include <iostream>
#include <iomanip>

#include "cfg.h"
#include "decode_macros.h"
#include "disasm.h"
#include "mmu.h"
#include "processor.h"
#include "simif.h"

static_assert(std::is_same_v<reg_t, uint64_t>);

struct t1emu_memory_vtable_t {
  uint8_t* (*addr_to_mem)(void* memory, reg_t addr);
  int (*mmio_load)(void* memory, reg_t addr, size_t len, uint8_t* bytes);
  int (*mmio_store)(void* memory, reg_t addr, size_t len, const uint8_t* bytes);
};

class t1emu_sim_t: public simif_t {
  void* m_memory;
  t1emu_memory_vtable_t m_vtable;

  cfg_t m_cfg;
  isa_parser_t m_isa_parser;
  processor_t m_proc;

public:
  t1emu_sim_t(
    void* memory,
    t1emu_memory_vtable_t const* vtable,
    cfg_t cfg,
    size_t vlen
  ):
    m_memory(memory),
    m_vtable(*vtable),
    m_cfg(std::move(cfg)),
    m_isa_parser(m_cfg.isa, m_cfg.priv),
    m_proc(
      &m_isa_parser,
      &m_cfg,
      this,
      0,
      true,
      nullptr,
      std::cerr
    )
  {
    m_proc.VU.lane_num = vlen / 32;
    m_proc.VU.lane_granularity = 32;
  }

  char* addr_to_mem(reg_t addr) override {
    return (char*)m_vtable.addr_to_mem(m_memory, addr);
  }

  bool mmio_fetch(reg_t addr, size_t len, uint8_t *bytes) override {
    // TODO: currently inst fetch is disallowed on mmio
    return false;
  }

  bool mmio_load(reg_t addr, size_t len, uint8_t *bytes) override {
    return (bool)m_vtable.mmio_load(m_memory, addr, len, bytes);
  }

  bool mmio_store(reg_t addr, size_t len, const uint8_t *bytes) override {
    return (bool)m_vtable.mmio_store(m_memory, addr, len, bytes);
  }

  virtual void proc_reset(unsigned id) override {
    // do nothing
  }

  virtual const char* get_symbol(uint64_t addr) override {
    throw std::logic_error("t1emu_sim_t::get_symbol not implemented");
  }

  const cfg_t& get_cfg() const override {
    return m_cfg;
  }

  const std::map<size_t, processor_t *> &
  get_harts() const override {
    throw std::logic_error("t1emu_sim_t::get_harts not implemented");
  }

  void reset_with_pc(reg_t new_pc) {
    m_proc.reset();
    m_proc.check_pc_alignment(new_pc);
    m_proc.get_state()->pc = new_pc;
  }

  void step_one() {
    reg_t pc = m_proc.get_state()->pc;
    mmu_t* mmu = m_proc.get_mmu();
    state_t* state = m_proc.get_state();

    try {
      insn_fetch_t fetch = mmu->load_insn(pc);
      reg_t new_pc = fetch.func(&m_proc, fetch.insn, pc);
      printf("pc=%08lx, new_pc=%08lx\n", pc, new_pc);
      if ((new_pc & 1) == 0) {
        state->pc = new_pc;
      } else {
        switch (new_pc) {
          case PC_SERIALIZE_BEFORE: state->serialized = true; break;
          case PC_SERIALIZE_AFTER: break;
          default: throw std::logic_error("invalid PC after fetch.func");
        }
      }
    } catch (trap_t &trap) {
      std::cerr << "Error: spike trapped with " << trap.name()
                << " (tval=" << std::uppercase << std::setfill('0')
                << std::setw(8) << std::hex << trap.get_tval()
                << ", tval2=" << std::setw(8) << std::hex << trap.get_tval2()
                << ", tinst=" << std::setw(8) << std::hex << trap.get_tinst()
                << ")" << std::endl;
      throw;
    } catch (std::exception& e) {
      std::cerr << e.what() << std::endl;
      throw;
    }
  }
};

extern "C" {
  t1emu_sim_t* t1emu_create(
    void* memory,
    t1emu_memory_vtable_t const* vtable,
    const char* isa_set,
    size_t vlen
  ) {
    cfg_t cfg;
    cfg.isa = strdup(isa_set);
    cfg.priv = "M";

    return new t1emu_sim_t(memory, vtable, cfg, vlen);
  }
  void t1emu_destroy(t1emu_sim_t* emu) {
    delete emu;
  }
  void t1emu_reset_with_pc(t1emu_sim_t* emu, reg_t new_pc) {
    emu->reset_with_pc(new_pc);
  }
  void t1emu_step_one(t1emu_sim_t* emu) {
    emu->step_one();
  }
}
