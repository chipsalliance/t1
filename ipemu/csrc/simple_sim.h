#ifdef IPEMU
#pragma once

#include <fstream>

#include <fmt/core.h>

#include "simif.h"
#include "spdlog_ext.h"
#include "uartlite.h"
#include "memory.h"

// A proxy to memory BFM, this is used in ipemu.
class simple_sim : public simif_t {
private:
  memory &mem;
public:
  explicit simple_sim(memory &mem): mem(mem) { };

  // should return NULL for MMIO addresses
  char *addr_to_mem(reg_t addr) override {
    if (mem.uart_addr <= addr && addr < mem.uart_addr + sizeof(uartlite_regs)) {
      return nullptr;
    }

    return &mem.mem[addr];
  }

  bool mmio_load(reg_t addr, size_t len, uint8_t *bytes) override {
    if (mem.uart_addr <= addr && addr < mem.uart_addr + sizeof(uartlite_regs)) {
      return mem.uart.do_read(addr - mem.uart_addr, len, bytes);
    }
    FATAL(fmt::format("Unknown MMIO load address ({:016X})", addr));
  }

  bool mmio_store(reg_t addr, size_t len, const uint8_t *bytes) override {
    if (mem.uart_addr <= addr && addr < mem.uart_addr + sizeof(uartlite_regs)) {
      bool res = mem.uart.do_write(addr - mem.uart_addr, len, bytes);
      while (mem.uart.exist_tx()) {
        std::cerr << mem.uart.getc();
        std::cerr.flush();
      }
      return res;
    }
    FATAL(fmt::format("Unknown MMIO load address ({:016X})", addr));
  }

  [[nodiscard]] const cfg_t &get_cfg() const override {
    FATAL("Unimplemented");
  }

  [[nodiscard]] const std::map<size_t, processor_t *> &
  get_harts() const override {
    FATAL("Unimplemented");
  }

  // Callback for processors to let the simulation know they were reset.
  void proc_reset(unsigned id) override {
    // maybe nothing to do
  }

  const char *get_symbol(uint64_t addr) override { FATAL("Unimplemented"); }
};
#endif