#pragma once

#include <fstream>

#include <glog/logging.h>

#include "glog_exception_safe.h"
#include "simif.h"

class simple_sim : public simif_t {
private:
  char *mem;
  size_t mem_size;

public:
  explicit simple_sim(size_t mem_size): mem_size(mem_size) {
    mem = new char[mem_size];
  }

  ~simple_sim() override {
    delete[] mem;
  }

  void load(const std::string &fname, size_t reset_vector) {
    std::ifstream fs(fname, std::ifstream::binary);
    CHECK_S(fs.is_open());

    size_t offset = reset_vector;
    while (!fs.eof()) {
      fs.read(&mem[offset], 1024);
      offset += fs.gcount();
    }
  }

  // should return NULL for MMIO addresses
  char *addr_to_mem(reg_t addr) override {
    CHECK_S(addr < mem_size) << fmt::format("memory out of bound ({:016X} >= {:016X})", addr, mem_size);
    return &mem[addr];
  }

  bool mmio_load(reg_t addr, size_t len, uint8_t *bytes) override {
    LOG(FATAL_S) << "not implemented";
  }

  bool mmio_store(reg_t addr, size_t len, const uint8_t *bytes) override {
    LOG(FATAL_S) << "not implemented";
  }

  [[nodiscard]] const cfg_t &get_cfg() const override {
    LOG(FATAL_S) << "not implemented";
  }

  [[nodiscard]] const std::map<size_t, processor_t*>& get_harts() const override {
    LOG(FATAL_S) << "not implemented";
  }

  // Callback for processors to let the simulation know they were reset.
  void proc_reset(unsigned id) override {
    // maybe nothing to do
  }

  const char *get_symbol(uint64_t addr) override {
    LOG(FATAL_S) << "not implemented";
  }
};
