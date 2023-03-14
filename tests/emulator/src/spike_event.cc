#include <fmt/core.h>
#include <glog/logging.h>

#include "disasm.h"

#include "spike_event.h"
#include "util.h"
#include "exceptions.h"
#include "glog_exception_safe.h"

std::string SpikeEvent::describe_insn() const {
  return fmt::format("pc={:08X}, bits={:08X}, disasm='{}'", pc, inst_bits, disasm);
}

void SpikeEvent::pre_log_arch_changes() {
  // TODO: support vl/vstart
  rd_bits = proc.get_state()->XPR[rd_idx];
  uint8_t *vreg_bytes_start = &proc.VU.elt<uint8_t>(0, 0);
  auto [start, len] = get_vrf_write_range();
  vd_write_record.vd_bytes = std::make_unique<uint8_t[]>(len);
  std::memcpy(vd_write_record.vd_bytes.get(), vreg_bytes_start + start, len);
}

void SpikeEvent::log_arch_changes() {
  state_t *state = proc.get_state();

  // record vrf writes
  // note that we do not need log_reg_write to find records, we just decode the insn and compare bytes
  uint8_t *vreg_bytes_start = &proc.VU.elt<uint8_t>(0, 0);
  auto [start, len] = get_vrf_write_range();
  for (int i = 0; i < len; i++) {
    uint32_t offset = start + i;
    uint8_t origin_byte = vd_write_record.vd_bytes[i], cur_byte = vreg_bytes_start[offset];
    if (origin_byte != cur_byte) {
      vrf_access_record.all_writes[offset] = { .byte = cur_byte };
      VLOG(1) << fmt::format("spike detect vrf change: vrf[{}, {}] from {:02X} to {:02X} [vrf_idx={}]",
                               offset / consts::vlen_in_bytes, offset % consts::vlen_in_bytes,
                               (int) origin_byte, (int) cur_byte, offset);
    }
  }

  for (auto [write_idx, data]: state->log_reg_write) {
    // in spike, log_reg_write is arrange:
    // xx0000 <- x
    // xx0001 <- f
    // xx0010 <- vreg
    // xx0011 <- vec
    // xx0100 <- csr
    if ((write_idx & 0xf) == 0b0000) {  // scalar rf
      uint32_t new_rd_bits = proc.get_state()->XPR[rd_idx];
      if (new_rd_bits != rd_bits) {
        rd_bits = new_rd_bits;
        is_rd_written = true;
        LOG(INFO) << fmt::format("spike detect scalar rf change: x[{}] from {} to {}", rd_idx, rd_bits, new_rd_bits);
      }
    } else {
      VLOG(1) << fmt::format("spike detect unknown reg change (idx = {:08X})", write_idx);
    }
  }

  for (auto mem_write: state->log_mem_write) {
    uint64_t address = std::get<0>(mem_write);
    uint64_t value = std::get<1>(mem_write);
    // Byte size_bytes
    uint8_t size_by_byte = std::get<2>(mem_write);
    LOG(INFO) << fmt::format("spike detect mem write {:08X} on {:08X} with size={}byte", value, address, size_by_byte);
    mem_access_record.all_writes[address].writes.push_back({ .size_by_byte = size_by_byte, .val = value });
  }

  for (auto mem_read: state->log_mem_read) {
    uint64_t address = std::get<0>(mem_read);
    // Byte size_bytes
    uint8_t size_by_byte = std::get<2>(mem_read);
    uint64_t value = 0;
    for (int i = 0; i < size_by_byte; ++i) {
      value += (uint64_t) impl->load(address + i) << (i * 8);
    }
    LOG(INFO) << fmt::format("spike detect mem read {:08X} on {:08X} with size={}byte", value, address, size_by_byte);
    mem_access_record.all_reads[address].reads.push_back({ .size_by_byte = size_by_byte, .val = value });
  }
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
  vnf = fetch.insn.v_nf();

  vill = proc.VU.vill;
  vxsat = proc.VU.vxsat->read();
  vl = proc.VU.vl->read();
  vstart = proc.VU.vstart->read();
  disasm = proc.get_disassembler()->disassemble(fetch.insn);

  pc = proc.get_state()->pc;
  inst_bits = fetch.insn.bits();
  uint32_t opcode = clip(inst_bits, 0, 6);
  uint32_t width = clip(inst_bits, 12, 14);
  uint32_t funct6 = clip(inst_bits, 26, 31);
  is_load = opcode == 0b0000111;
  is_store = opcode == 0b0100111;
  is_widening = opcode == 0b1010111 && (funct6 >> 4) == 0b11;
  is_mask_vd = opcode == 0b1010111 && (funct6 >> 3 == 0b011 || funct6 == 0b010001);
  is_exit_insn = opcode == 0b1110011;
  is_vfence_insn = opcode == 0b1010111 && width == 0b111;

  is_issued = false;

  lsu_idx = consts::lsuIdxDefault;  // default lsu_idx
}

void SpikeEvent::drive_rtl_req(const VInstrInterfacePoke &v_inst) const {
  *v_inst.valid = true;
  *v_inst.inst = inst_bits;
  *v_inst.src1Data = rs1_bits;
  *v_inst.src2Data = rs2_bits;
//  v_inst.storeBufferClear = true;  // TODO: why no buffer clear
}

void SpikeEvent::drive_rtl_csr(const VCsrInterfacePoke &v_csr) const {
  *v_csr.vSew = vsew;
  *v_csr.vlmul = vlmul;
  *v_csr.vma = vma;
  *v_csr.vta = vta;
  *v_csr.vl = vl;
  *v_csr.vStart = vstart;
  *v_csr.vxrm = vxrm;
  *v_csr.ignoreException = false;
}

void SpikeEvent::check_is_ready_for_commit() {
  for (auto &[addr, mem_write]: mem_access_record.all_writes) {
    if (mem_write.num_completed_writes != mem_write.writes.size()) {
      LOG(FATAL_S) << fmt::format(": [{}] expect to write mem {:08X}, not executed when commit ({})",
                                impl->get_t(), addr, pc, describe_insn());
    }
  }
  for (auto &[addr, mem_read]: mem_access_record.all_reads) {
    if (mem_read.num_completed_reads != mem_read.reads.size()) {
      LOG(FATAL_S) << fmt::format(": [{}] expect to read mem {:08X}, not executed when commit ({})",
                                impl->get_t(), addr, describe_insn());
    }
  }
  for (auto &[idx, vrf_write]: vrf_access_record.all_writes) {
    CHECK_S(vrf_write.executed) << fmt::format(": [{}] expect to write vrf [{}][{}], not executed when commit ({})",
                              impl->get_t(), idx / consts::vlen_in_bytes, idx % consts::vlen_in_bytes, describe_insn());
  }
}

void SpikeEvent::record_rd_write(const VRespInterface &v_resp) {
  // TODO: rtl should indicate whether resp_bits_data is valid
  if (is_rd_written) {
    CHECK_EQ_S(v_resp.data, rd_bits) << fmt::format(": [{}] expect to write rd[{}] = {}, actual {}",
                                                             impl->get_t(), rd_idx, rd_bits, v_resp.data);
  }
}

std::pair<uint32_t, uint32_t> SpikeEvent::get_vrf_write_range() const {
  if (is_store) {
    return {0, 0};  // store will not write vrf
  } else if (is_load) {
    uint32_t vd_bytes_start = rd_idx * consts::vlen_in_bytes;
    uint32_t len = vlmul & 0b100
        ? consts::vlen_in_bytes * (1 + vnf)
        : consts::vlen_in_bytes * (1 + vnf) << vlmul;
    return {vd_bytes_start, len};
  } else {
    uint32_t vd_bytes_start = rd_idx * consts::vlen_in_bytes;

    if (is_mask_vd) {
      return {vd_bytes_start, consts::vlen_in_bytes};
    }

    uint32_t len = vlmul & 0b100
                   ? consts::vlen_in_bytes >> (8 - vlmul)
                   : consts::vlen_in_bytes << vlmul;

    return {vd_bytes_start, is_widening ? len*2 : len};
  }
}
