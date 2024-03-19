#pragma once

#include "mmio_dev.hpp"

#include <fstream>
#include <iostream>
#include <linux/elf.h>

class mmio_mem : public mmio_dev  {
    public:
        mmio_mem(size_t size_bytes) {
            mem = new unsigned char[size_bytes];
            mem_size = size_bytes;
        }
        mmio_mem(size_t size_bytes, const unsigned char *init_binary, size_t init_binary_len): mmio_mem(size_bytes) {
            // Initalize memory 
            assert(init_binary_len <= size_bytes);
            memcpy(mem,init_binary,init_binary_len);
        }
        mmio_mem(size_t size_bytes, const char *init_file): mmio_mem(size_bytes) {
            load_binary(0,init_file);
        }
        ~mmio_mem() {
            delete [] mem;
        }
        bool do_read(uint64_t start_addr, uint64_t size, uint8_t* buffer) {
            if (start_addr + size <= mem_size) {
                memcpy(buffer,&mem[start_addr],size);
                return true;
            }
            else if (allow_warp) {
                start_addr %= mem_size;
                if (start_addr + size <= mem_size) {
                    memcpy(buffer,&mem[start_addr],size);
                    return true;
                }
                else return false;
            }
            else return false;
        }
        bool do_write(uint64_t start_addr, uint64_t size, const uint8_t* buffer) {
            if (start_addr + size <= mem_size) {
                memcpy(&mem[start_addr],buffer,size);
                return true;
            }
            else if (allow_warp) {
                start_addr %= mem_size;
                if (start_addr + size <= mem_size) {
                    memcpy(&mem[start_addr],buffer,size);
                    return true;
                }
                else return false;
            }
            else return false;
        }
        void load_binary(uint64_t start_addr, const char *init_file) {
            std::ifstream file(init_file,std::ios::in | std::ios::binary | std::ios::ate);
            size_t file_size = file.tellg();
            file.seekg(std::ios_base::beg);
            if (start_addr >= mem_size || file_size > mem_size - start_addr) {
                std::cerr << "memory size is not big enough for init file." << std::endl;
                file_size = mem_size;
            }
            file.read((char*)mem+start_addr,file_size);
        }
        Elf32_Addr load_elf(uint64_t base_addr, const char *init_file) {
            std::ifstream file(init_file, std::ios::binary);
            file.exceptions(std::ios::failbit);

            Elf32_Ehdr ehdr;
            file.read(reinterpret_cast<char *>(&ehdr), sizeof(ehdr));
            if (!(ehdr.e_machine == EM_RISCV && ehdr.e_type == ET_EXEC
                    && ehdr.e_ident[EI_CLASS] == ELFCLASS32)) {
                std::cerr << "ehdr check failed when loading elf" << std::endl;
                return -1;
            }
            if (ehdr.e_phentsize != sizeof(elf32_phdr)) {
                std::cerr << "ehdr.e_phentsize does not equal to elf32_phdr" << std::endl;
                return -1;
            }

            for (size_t i = 0; i < ehdr.e_phnum; i++) {
              auto phdr_offset = ehdr.e_phoff + i * ehdr.e_phentsize;
              Elf32_Phdr phdr;
              file.seekg((long)phdr_offset)
                  .read(reinterpret_cast<char *>(&phdr), sizeof(phdr));
              if (phdr.p_type == PT_LOAD) {
                if (phdr.p_paddr - base_addr + phdr.p_filesz >= mem_size) {
                    std::cerr << "phdr p_paddr + p_filesz check failed" << std::endl;
                    return -1;
                }
                file.seekg((long)phdr.p_offset)
                    .read(reinterpret_cast<char *>(&mem[phdr.p_paddr-base_addr]), phdr.p_filesz);
              }
            }
            return ehdr.e_entry;
        }
        void save_binary(const char *filename) {
            std::ofstream file(filename, std::ios::out | std::ios::binary);
            file.write((char*)mem, mem_size);
        }
        void set_allow_warp(bool value) {
            allow_warp = true;
        }
        unsigned char *get_mem_ptr() {
            return mem;
        }
    private:
        unsigned char *mem;
        size_t mem_size;
        bool allow_warp = false;
};
