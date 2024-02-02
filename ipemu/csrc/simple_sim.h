#pragma once

#include <fstream>

#include <fmt/core.h>

#include "simif.h"
#include "spdlog-ext.h"
#include "uartlite.h"

class simple_sim : public simif_t {
private:
  char *mem;
  size_t mem_size;
  uartlite uart;
  reg_t uart_addr = 0x90000000;

public:
  explicit simple_sim(size_t mem_size) : mem_size(mem_size) {
    mem = new char[mem_size];
  }

  ~simple_sim() override { delete[] mem; }

  struct load_elf_result_t {
    uint32_t entry_addr;
  };
  load_elf_result_t load_elf(const std::string &fname);

  // should return NULL for MMIO addresses
  char *addr_to_mem(reg_t addr) override {
    if (uart_addr <= addr && addr < uart_addr + sizeof(uartlite_regs)) {
      return NULL;
    }
    CHECK_LE(addr, mem_size,
             fmt::format("memory out of bound ({:016X} >= {:016X})", addr,
                         mem_size));
    return &mem[addr];
  }

  bool mmio_load(reg_t addr, size_t len, uint8_t *bytes) override {
    if (uart_addr <= addr && addr < uart_addr + sizeof(uartlite_regs)) {
      return uart.do_read(addr - uart_addr, len, bytes);
    }
    FATAL(fmt::format("Unknown MMIO load address ({:016X})", addr));
  }

  bool mmio_store(reg_t addr, size_t len, const uint8_t *bytes) override {
    if (uart_addr <= addr && addr < uart_addr + sizeof(uartlite_regs)) {
      bool res = uart.do_write(addr - uart_addr, len, bytes);
      while (uart.exist_tx()) {
        std::cerr << uart.getc();
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
