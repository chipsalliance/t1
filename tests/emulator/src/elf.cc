#include "simple_sim.h"

#include <linux/elf.h>

simple_sim::load_elf_result_t simple_sim::load_elf(const std::string &fname) {
  try {
    std::ifstream fs(fname, std::ios::binary);
    fs.exceptions(std::ios::failbit);

    Elf32_Ehdr ehdr;
    fs.read(reinterpret_cast<char *>(&ehdr), sizeof(ehdr));
    CHECK_S(ehdr.e_machine == EM_RISCV && ehdr.e_type == ET_EXEC && ehdr.e_ident[EI_CLASS] == ELFCLASS32);
    CHECK_S(ehdr.e_phentsize == sizeof(elf32_phdr));

    for (size_t i = 0; i < ehdr.e_phnum; i++) {
      auto phdr_offset = ehdr.e_phoff + i * ehdr.e_phentsize;
      Elf32_Phdr phdr;
      fs.seekg((long) phdr_offset).read(reinterpret_cast<char *>(&phdr), sizeof(phdr));
      if (phdr.p_type == PT_LOAD) {
        CHECK_S(phdr.p_paddr + phdr.p_filesz < mem_size);
        fs.seekg((long) phdr.p_offset).read(reinterpret_cast<char *>(&mem[phdr.p_paddr]), phdr.p_filesz);
        VLOG(1) << fmt::format("load elf segment {} at file phdr_offset {:08X} to paddr {:08X}", i, phdr.p_offset, phdr.p_paddr);
      }
    }
    return { .entry_addr = ehdr.e_entry };
  } catch (std::ios_base::failure&) {
    throw std::system_error { errno, std::generic_category(), fname };
  }
}
