#include "simple_sim.h"

#include <fmt/core.h>
#include <linux/elf.h>
#include <endian.h>

// convert little-endian integral type to host-endian
template<typename T>
T from_le(T value) {
  static_assert(std::is_integral<T>::value, "T must be an integral type");

  if constexpr (sizeof(T) == 1) {
    return value;
  } else if constexpr (sizeof(T) == 2) {
    return le16toh(value);
  } else if constexpr (sizeof(T) == 4) {
    return le32toh(value);
  } else if constexpr (sizeof(T) == 8) {
    return le64toh(value);
  } else {
    static_assert(sizeof(T) <= 8, "Unsupported type size");
  }
}

void copy_from_fs(std::ifstream &ifs, std::streamoff offset, std::streamoff size, void *dst) {
  ifs.clear();
  ifs.seekg(offset);
  ifs.read(reinterpret_cast<char *>(dst), size);
}

template<typename T>
T read_from_fs(std::ifstream &ifs, std::streamoff offset) {
  T t{};
  copy_from_fs(ifs, offset, sizeof(T), &t);
  return t;
}

simple_sim::load_elf_result_t simple_sim::load_elf32_little_endian(const std::string &fname) {
  try {
    std::ifstream fs(fname, std::ios::binary);
    fs.exceptions(std::ios::failbit);

    auto ehdr = read_from_fs<Elf32_Ehdr>(fs, 0);
    CHECK(std::memcmp(ehdr.e_ident, ELFMAG, SELFMAG) == 0, "elf magic not match");
    CHECK(ehdr.e_machine == EM_RISCV, "elf not in RISCV");
    CHECK(ehdr.e_type == ET_EXEC, "elf not executable");
    CHECK(ehdr.e_ident[EI_DATA] == ELFDATA2LSB, "elf not little endian");
    CHECK(ehdr.e_ident[EI_CLASS] == ELFCLASS32, "elf not in 32bit");

    for (size_t i = 0; i < from_le(ehdr.e_phnum); i++) {
      auto phdr_offset = from_le(ehdr.e_phoff) + i * from_le(ehdr.e_phentsize);
      auto phdr = read_from_fs<Elf32_Phdr>(fs, (std::streamoff) phdr_offset);
      if (from_le(phdr.p_type) == PT_LOAD) {
        auto paddr = from_le(phdr.p_paddr);
        auto filesz = from_le(phdr.p_filesz);

        CHECK(paddr + filesz < mem_size,
              "phdr p_paddr + p_filesz check failed");
        fs.clear();
        fs.seekg(from_le(phdr.p_offset))
          .read(reinterpret_cast<char *>(&mem[paddr]), filesz);
        Log("LoadElf")
          .with("segment", i)
          .with("phdr_offset", fmt::format("{:08X}", phdr.p_offset))
          .with("paddr_range", fmt::format("{:08X}-{:08X}", phdr.p_paddr,
                                           phdr.p_paddr + phdr.p_memsz))
          .warn();
      }
    }

    // read section string section
    auto shoff = from_le(ehdr.e_shoff);
    auto shentsize = from_le(ehdr.e_shentsize);
    auto shstrndx = from_le(ehdr.e_shstrndx);
    auto section_string_shdr_offset = shoff + shstrndx * shentsize;
    auto section_string_shdr = read_from_fs<Elf32_Shdr>(fs, section_string_shdr_offset);
    std::vector<char> section_string_table(from_le(section_string_shdr.sh_size));
    copy_from_fs(fs,
                 from_le(section_string_shdr.sh_offset),
                 from_le(section_string_shdr.sh_size),
                 section_string_table.data());

    // iterate over section headers to find the symbol string table
    std::vector<char> string_table;
    for (int i = 0; i < from_le(ehdr.e_shnum); ++i) {
      auto shdr = read_from_fs<Elf32_Shdr>(fs, shoff + i * shentsize);
      if (from_le(shdr.sh_type) == SHT_STRTAB &&
          std::string(&section_string_table[from_le(shdr.sh_name)]) == ".strtab") {
        Log("size").with("size", shdr.sh_size).warn();
        string_table.resize(from_le(shdr.sh_size));
        copy_from_fs(fs, from_le(shdr.sh_offset), from_le(shdr.sh_size), string_table.data());
      }
    }

    if (string_table.empty()) {
      Log("LoadElf").warn("failed to find .strtab");
    } else {
      // iterate over section headers to find the symbol table
      for (int i = 0; i < from_le(ehdr.e_shnum); ++i) {
        auto shdr = read_from_fs<Elf32_Shdr>(fs, shoff + i * shentsize);
        if (from_le(shdr.sh_type) == SHT_SYMTAB && std::string(&section_string_table[shdr.sh_name]) == ".symtab") {
          auto entsize = from_le(shdr.sh_entsize);
          unsigned int num_sym = from_le(shdr.sh_size) / entsize;
          for (int j = 0; j < num_sym; ++j) {
            auto offset = from_le(shdr.sh_offset) + j * entsize;
            auto sym = read_from_fs<Elf32_Sym>(fs, (std::streamoff) offset);

            if (ELF32_ST_TYPE(from_le(sym.st_info)) == STT_FUNC) { // Only considering function symbols
              // read the name from the string table
              std::string name(&string_table.at(from_le(sym.st_name)));
              function_symtab[from_le(sym.st_value)] = {.name = name, .info = from_le(sym.st_info)}; // Add to map
            }
          }
          break;
        }
      }
    }

    return {.entry_addr = ehdr.e_entry};
  } catch (std::ios_base::failure &f) {
    Log("LoadElf")
      .with("errno", errno)
      .with("fname", fname)
      .with("reason", f.what())
      .fatal();
  }
}
