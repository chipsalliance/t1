#include "simple_sim.h"

#include <fmt/core.h>
#include <linux/elf.h>

simple_sim::load_elf_result_t simple_sim::load_elf(const std::string &fname) {
  try {
    std::ifstream fs(fname, std::ios::binary);
    fs.exceptions(std::ios::failbit);

    Elf32_Ehdr ehdr;
    fs.read(reinterpret_cast<char *>(&ehdr), sizeof(ehdr));
    CHECK(ehdr.e_machine == EM_RISCV && ehdr.e_type == ET_EXEC && ehdr.e_ident[EI_CLASS] == ELFCLASS32, "ehdr check failed when loading elf");
    CHECK_EQ(ehdr.e_phentsize, sizeof(elf32_phdr), "ehdr.e_phentsize does not equal to elf32_phdr");

    for (size_t i = 0; i < ehdr.e_phnum; i++) {
      auto phdr_offset = ehdr.e_phoff + i * ehdr.e_phentsize;
      Elf32_Phdr phdr;
      fs.seekg((long) phdr_offset).read(reinterpret_cast<char *>(&phdr), sizeof(phdr));
      if (phdr.p_type == PT_LOAD) {
        CHECK(phdr.p_paddr + phdr.p_filesz < mem_size, "phdr p_paddr + p_filesz check failed");
        fs.seekg((long) phdr.p_offset).read(reinterpret_cast<char *>(&mem[phdr.p_paddr]), phdr.p_filesz);
        Log("LoadElfResult")
          .with("segment", i)
          .with("phdr_offset", fmt::format("{:08X}", phdr.p_offset))
          .with("paddr_range", fmt::format("{:08X}-{:08X}", phdr.p_paddr, phdr.p_paddr + phdr.p_memsz))
          .trace();
      }
    }
    return { .entry_addr = ehdr.e_entry };
  } catch (std::ios_base::failure&) {
    throw std::system_error { errno, std::generic_category(), fname };
  }
}
