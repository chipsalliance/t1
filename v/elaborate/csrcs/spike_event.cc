#include <fmt/core.h>
#include <glog/logging.h>

#include "spike_event.h"
#include "disasm.h"
#include "util.h"
#include "tl_interface.h"

std::string SpikeEvent::describe_insn() const {
  return fmt::format("pc={:08X}, bits={:08X}, disasm='{}'", pc, inst_bits, proc.get_disassembler()->disassemble(inst_bits));
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

void SpikeEvent::pre_log_arch_changes() {
  // TODO: support vl/vstart
  rd_bits = proc.get_state()->XPR[rd_idx];
  uint8_t *vd_bits_start = &proc.VU.elt<uint8_t>(rd_idx, 0);
  LOG_ASSERT(vlmul < 4) << ": fractional vlmul not supported yet";  // TODO: support fractional vlmul
  uint32_t len = consts::vlen_in_bits << vlmul / 8;
  vd_write_record.vd_bytes = std::make_unique<uint8_t[]>(len);
  std::memcpy(vd_write_record.vd_bytes.get(), vd_bits_start, len);
}

void SpikeEvent::log_arch_changes() {
  state_t *state = proc.get_state();

  for (auto [write_idx, data]: state->log_reg_write) {
    // in spike, log_reg_write is arrange:
    // xx0000 <- x
    // xx0001 <- f
    // xx0010 <- vreg
    // xx0011 <- vec
    // xx0100 <- csr
    if ((write_idx & 0xf) == 0b0010) {  // vreg
      auto idx = (write_idx >> 4);
      LOG_ASSERT(idx == rd_idx) << fmt::format(": expect to write vrf[{}], detect writing vrf[{}]", rd_idx, idx);
      LOG_ASSERT(idx < 32) << fmt::format(": log_reg_write idx ({}) out of bound", idx);

      uint8_t *vd_bits_start = &proc.VU.elt<uint8_t>(rd_idx, 0);
      uint32_t len = consts::vlen_in_bits << vlmul / 8;
      for (int i = 0; i < len; i++) {
        uint8_t origin_byte = vd_write_record.vd_bytes[i], cur_byte = vd_bits_start[i];
        if (origin_byte != cur_byte) {
          LOG(INFO) << fmt::format("spike detect vrf change: vrf[{}, {}] from {} to {}", idx, i, (int) origin_byte, (int) cur_byte);
        }
      }

    } else if ((write_idx & 0xf) == 0b0000) {  // scalar rf
      uint32_t new_rd_bits = proc.get_state()->XPR[rd_idx];
      if (new_rd_bits != rd_bits) {
        rd_bits = new_rd_bits;
        is_rd_written = true;
        LOG(INFO) << fmt::format("spike detect scalar rf change: x[{}] from {} to {}", rd_idx, rd_bits, new_rd_bits);
      }
    } else {
      LOG(INFO) << fmt::format("spike detect unknown reg change (idx = {:08X})", write_idx);
    }
  }

  for (auto mem_write: state->log_mem_write) {
    uint64_t address = std::get<0>(mem_write);
    uint64_t value = std::get<1>(mem_write);
    // Byte size_bytes
    uint8_t size_by_byte = std::get<2>(mem_write);
    LOG(INFO) << fmt::format("spike detect mem write {:08X} on {:08X} with size={}byte", value, address, size_by_byte);
    mem_access_record.all_writes[address] = { .size_by_byte = size_by_byte, .val = value };
  }

  for (auto mem_read: state->log_mem_read) {
    uint64_t address = std::get<0>(mem_read);
    uint64_t value = std::get<1>(mem_read);
    // Byte size_bytes
    uint8_t size_by_byte = std::get<2>(mem_read);
    LOG(INFO) << fmt::format("spike detect mem read {:08X} on {:08X} with size={}byte", value, address, size_by_byte);
    mem_access_record.all_reads[address] = { .size_by_byte = size_by_byte, .val = value };
  }

  state->log_reg_write.clear();
  state->log_mem_read.clear();
  state->log_mem_write.clear();
}

SpikeEvent::SpikeEvent(processor_t &proc, insn_fetch_t &fetch, VBridgeImpl *impl): proc(proc), impl(impl) {
  auto &xr = proc.get_state()->XPR;
  rs1_bits = xr[fetch.insn.rs1()];
  rs2_bits = xr[fetch.insn.rs2()];
  rd_idx = fetch.insn.rd();

  is_rd_written = false;

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

  lsu_idx = consts::lsuIdxDefault;  // default lsu_idx
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

void SpikeEvent::check_is_ready_for_commit() {
  for (auto &[addr, mem_write]: mem_access_record.all_writes) {
    if (!mem_write.executed) {
      LOG(FATAL) << fmt::format("expect to read {:08X}, not executed when commit (pc={:08X}, insn='{}'",
                                addr, pc, describe_insn());
    }
  }
  for (auto &[addr, mem_read]: mem_access_record.all_reads) {
    if (!mem_read.executed) {
      LOG(FATAL) << fmt::format("expect to read {:08X}, not executed when commit (pc={:08X}, insn='{}'",
                                addr, pc, describe_insn());
    }
  }
}

void SpikeEvent::record_rd_write(VV &top) {
  // TODO: rtl should indicate whether resp_bits_data is valid
  if (is_rd_written) {
    LOG_ASSERT(top.resp_bits_data == rd_bits) << fmt::format(": expect to write rd[{}] = {}, actual {}",
                                                             rd_idx, rd_bits, top.resp_bits_data);
  }
}
