#ifndef __SPIKE_INTERFCES_H__
#define __SPIKE_INTERFCES_H__

#include "cfg.h"
#include "decode_macros.h"
#include "disasm.h"
#include "mmu.h"
#include "processor.h"
#include "simif.h"
#include "spike_interfaces_c.h"

#ifdef __cplusplus
extern "C" {
#endif

ffi_callback ffi_addr_to_mem;
extern void *ffi_target;
std::vector<uint32_t> reg_write_index_vec;

class t1_sim_t : public simif_t {
public:
  t1_sim_t() {}
  ~t1_sim_t() {}
  char *addr_to_mem(reg_t addr) override {
    return ffi_addr_to_mem(ffi_target, addr);
  }
  bool mmio_load(reg_t addr, size_t len, uint8_t *bytes) override {
    throw std::logic_error("not implemented");
  }
  bool mmio_store(reg_t addr, size_t len, const uint8_t *bytes) override {
    throw std::logic_error("not implemented");
  }
  virtual void proc_reset(unsigned id) override {}
  virtual const char *get_symbol(uint64_t addr) override {
    throw std::logic_error("not implemented");
  }
  [[nodiscard]] const cfg_t &get_cfg() const override {
    throw std::logic_error("not implemented");
  }
  [[nodiscard]] const std::map<size_t, processor_t *> &
  get_harts() const override {
    throw std::logic_error("not implemented");
  }
};

class Spike {
public:
  Spike(const char *set, const char *lvl, size_t lane_number);
  processor_t *get_proc() { return &proc; }

private:
  cfg_t cfg;
  t1_sim_t sim;
  isa_parser_t isa;
  processor_t proc;
};

struct spike_t {
  Spike *s;
  ffi_callback ffi_addr_to_mem;
};
struct spike_processor_t {
  processor_t *p;
};
struct spike_state_t {
  state_t *s;
};
struct spike_mmu_t {
  mmu_t *m;
};

#ifdef __cplusplus
}
#endif

#endif // __SPIKE_INTERFCES_H__
