#include "simple_sim.h"

#include <linux/elf.h>

simple_sim::load_elf_result_t simple_sim::load_elf(const std::string &fname) {
  std::ifstream fs(fname, std::ios::binary);
  Elf32_Ehdr ehdr;
  fs.read(reinterpret_cast<char *>(&ehdr), sizeof(ehdr));
  CHECK_S(ehdr.e_machine == EM_RISCV && ehdr.e_type == ET_EXEC && ehdr.e_ident[EI_CLASS] == ELFCLASS32);
  CHECK_S(ehdr.e_phentsize == sizeof(elf32_phdr));
  for (size_t i = 0; i < ehdr.e_phnum; i++) {
    auto offset = ehdr.e_phoff + i * ehdr.e_phentsize;
    Elf32_Phdr phdr;
    fs.seekg((long) offset)
      .read(reinterpret_cast<char *>(&phdr), sizeof(phdr));
    if (phdr.p_type == PT_LOAD) {
      fs.seekg((long) phdr.p_offset)
        .read(reinterpret_cast<char *>(&mem[phdr.p_paddr]), phdr.p_filesz);
      VLOG(1) << fmt::format("load elf segment {} at file offset {:08X} to paddr {:08X}", i, phdr.p_offset, phdr.p_paddr);
    }
  }
  return { .entry_addr = ehdr.e_entry };
}
