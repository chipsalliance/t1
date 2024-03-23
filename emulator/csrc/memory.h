#pragma once

#include <fstream>
#include <fmt/core.h>
#include "uartlite.h"

// This is a entire memory behavior model.
class memory {
  // TODO: provide API to register different memory BFM after we have OM.
  //       e.g. UART, dramsim.
  public:
  char *mem;
  size_t mem_size;
  uartlite uart;
  uint64_t uart_addr;

  struct load_elf_result_t {
    uint32_t entry_addr;
  };
  explicit memory(size_t mem_size, uint64_t uart_addr) :
    mem_size(mem_size),
    uart_addr(uart_addr) {
      mem = new char[mem_size];
  }

  load_elf_result_t load_elf(const std::string &fname);

  ~memory() { delete[] mem; }
};