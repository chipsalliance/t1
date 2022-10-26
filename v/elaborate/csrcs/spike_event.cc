#include <fmt/core.h>
#include <glog/logging.h>

#include "spike_event.h"
#include "disasm.h"
#include "util.h"
#include "tl_interface.h"

std::string SpikeEvent::get_insn_disasm() const {
  return fmt::format("PC: {:X}, ASM: {:08X}, DISASM: {}", pc, inst_bits, proc.get_disassembler()->disassemble(inst_bits));
}

uint64_t SpikeEvent::mem_load(uint64_t addr, uint32_t size) {
  switch (size) {
    case 0:
      return proc.get_mmu()->load_uint8(addr);
    case 1:
      return proc.get_mmu()->load_uint16(addr);
    case 2:
      return proc.get_mmu()->load_uint32(addr);
    default:
      LOG(FATAL) << fmt::format("unknown load size {}", size);
  }
}

void SpikeEvent::log() {
  state_t *state = proc.get_state();

  for (auto reg: state->log_reg_write) {
    // in spike, log_reg_write is arrange:
    // xx0000 <- x
    // xx0001 <- f
    // xx0010 <- vreg
    // xx0011 <- vec
    // xx0100 <- csr
    if ((reg.first & 0xf) == 2) {
      auto idx = (reg.first >> 4);
      if (idx > 31) {
        LOG(FATAL) << fmt::format("SPIKE is crazy");
      }
      LOG(INFO) << fmt::format("Reg Access: {}, TODO: access contents", idx);
      continue;
    } else if (false) {
      // TODO: write scalar register.
    }
  }
  for (auto mem_write: state->log_mem_write) {
    uint64_t address = std::get<0>(mem_write);
    uint64_t value = std::get<1>(mem_write);
    // Byte size
    uint8_t size = std::get<2>(mem_write);
    LOG(INFO) << fmt::format("Memory Store {:X} to {:X} with size = {} ", value, address, size);
  }
  for (auto mem_read: state->log_mem_read) {
    uint64_t address = std::get<0>(mem_read);
    uint8_t size = std::get<2>(mem_read);
    for (int i = 0; i < size; ++i) {
      uint8_t value = impl->load(address + i);
      LOG(INFO) << fmt::format("Memory Load(size: {}) from 0x{:X}, offset {}, value accessed: {:X}", size, address, i, value);
    }
  }
}

SpikeEvent::SpikeEvent(processor_t &proc, insn_fetch_t &fetch): proc(proc) {
  auto &xr = proc.get_state()->XPR;
  rs1_bits = xr[fetch.insn.rs1()];
  rs2_bits = xr[fetch.insn.rs2()];

  uint64_t vtype = proc.VU.vtype->read();
  vlmul = clip(vtype, 0, 2);
  vma = clip(vtype, 7, 7);
  vta = clip(vtype, 6, 6);
  vsew = clip(vtype, 3, 5);
  vlmul = clip(vtype, 0, 2);
  vill = clip(vtype, 31, 31);
  vxrm = proc.VU.vxrm->read();

  vill = proc.VU.vill;
  vxsat = proc.VU.vxsat->read();
  vl = proc.VU.vl->read();
  vstart = proc.VU.vl->read();

  pc = proc.get_state()->pc;
  inst_bits = fetch.insn.bits();
  uint32_t opcode = clip(inst_bits, 0, 6);
  is_load = opcode == 0b111;
  is_store = opcode == 0b100111;

  is_issued = false;
  is_committed = false;

  lsu_idx = 255;  // default lsu_idx
}

void SpikeEvent::drive_rtl_req(VV &top) const {
  top.req_valid = true;
  top.req_bits_inst = inst_bits;
  top.req_bits_src1Data = rs1_bits;
  top.req_bits_src2Data = rs2_bits;
  top.storeBufferClear = true;
}

void SpikeEvent::drive_rtl_csr(VV &top) const {
  top.csrInterface_vSew = vsew;
  top.csrInterface_vlmul = vlmul;
  top.csrInterface_vma = vma;
  top.csrInterface_vta = vta;
  top.csrInterface_vl = vl;
  top.csrInterface_vStart = vstart;
  top.csrInterface_vxrm = vxrm;
  top.csrInterface_ignoreException = false;
}
