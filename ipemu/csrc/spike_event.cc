#ifdef IPEMU
#include <fmt/core.h>

#include "disasm.h"

#include "exceptions.h"
#include "spdlog_ext.h"
#include "spike_event.h"
#include "util.h"

std::string SpikeEvent::describe_insn() const {
  return fmt::format("pc={:08X}, bits={:08X}, disasm='{}'", pc, inst_bits,
                     disasm);
}

json SpikeEvent::jsonify_insn() const {
  return json{
      {"pc", fmt::format("{:08X}", pc)},
      {"bits", fmt::format("{:08X}", inst_bits)},
      {"disasm", disasm},
  };
}

uint32_t extract_f32(freg_t f) { return (uint32_t)f.v[0]; }

void SpikeEvent::pre_log_arch_changes() {
  // TODO: support vl/vstart
  if (is_rd_fp) {
    rd_bits = extract_f32(proc.get_state()->FPR[rd_idx]);
  } else {
    rd_bits = proc.get_state()->XPR[rd_idx];
  }
  uint8_t *vreg_bytes_start = &proc.VU.elt<uint8_t>(0, 0);
  auto [start, len] = get_vrf_write_range();
  vd_write_record.vd_bytes = std::make_unique<uint8_t[]>(len);
  std::memcpy(vd_write_record.vd_bytes.get(), vreg_bytes_start + start, len);
}

void SpikeEvent::log_arch_changes() {
  state_t *state = proc.get_state();

  // record vrf writes
  // note that we do not need log_reg_write to find records, we just decode the
  // insn and compare bytes
  uint8_t *vreg_bytes_start = &proc.VU.elt<uint8_t>(0, 0);
  auto [start, len] = get_vrf_write_range();
  for (int i = 0; i < len; i++) {
    uint32_t offset = start + i;
    uint8_t origin_byte = vd_write_record.vd_bytes[i],
            cur_byte = vreg_bytes_start[offset];
    if (origin_byte != cur_byte) {
      vrf_access_record.all_writes[offset] = {.byte = cur_byte};
      Log("SpikeVRFChange")
          .with("vrf", std::vector{offset / impl->config.vlen_in_bytes,
                                   offset % impl->config.vlen_in_bytes})
          .with("change_from", (int)origin_byte)
          .with("change_to", (int)cur_byte)
          .with("vrf_idx", offset)
          .trace("spike detect vrf change");
    }
  }

  for (auto [write_idx, data] : state->log_reg_write) {
    // in spike, log_reg_write is arrange:
    // xx0000 <- x
    // xx0001 <- f
    // xx0010 <- vreg
    // xx0011 <- vec
    // xx0100 <- csr
    if ((write_idx & 0xf) == 0b0000) { // scalar rf
      uint32_t new_rd_bits = proc.get_state()->XPR[rd_idx];
      if (new_rd_bits != rd_bits) {
        rd_bits = new_rd_bits;
        is_rd_written = true;
        Log("ScalarRFChange")
            .with("rd_index", rd_idx)
            .with("change_from", rd_bits)
            .with("change_to", new_rd_bits)
            .trace("spike detect scalar rf change");
      }
    } else if ((write_idx & 0xf) == 0b0001) {
      uint32_t new_fd_bits = extract_f32(proc.get_state()->FPR[rd_idx]);
      if (new_fd_bits == rd_bits) {
        rd_bits = new_fd_bits;
        is_rd_written = true;
        Log("FloatRFChange")
            .with("rd_index", rd_idx)
            .with("change_from", rd_bits)
            .with("change_to", new_fd_bits)
            .trace("spike detect float rf change");
      }
    } else {
      Log("UnknownRegChange")
          .with("idx", fmt::format("{:08X}", write_idx))
          .trace("spike detect unknown reg change");
    }
  }

  for (auto mem_write : state->log_mem_write) {
    // spike would sign-extend memory address to uint64_t, even in rv32, hence we should mask higher bits
    uint64_t address = std::get<0>(mem_write) & 0xffffffff;

    uint64_t value = std::get<1>(mem_write);
    // Byte size_bytes
    uint8_t size_by_byte = std::get<2>(mem_write);
    Log("MemWrite")
        .with("value", fmt::format("{:08X}", value))
        .with("address", fmt::format("{:08X}", address))
        .with("size_by_byte", size_by_byte)
        .info("spike detect mem write");
    for (size_t offset = 0; offset < size_by_byte; offset++) {
      uint8_t val_byte = (value >> (8 * offset)) & 0xff;
      mem_access_record.all_writes[address + offset].writes.push_back(
          {.val = val_byte});
    }
  }

  for (auto mem_read : state->log_mem_read) {
    // spike would sign-extend memory address to uint64_t, even in rv32, hence we should mask higher bits
    uint64_t address = std::get<0>(mem_read) & 0xffffffff;

    // Byte size_bytes
    uint8_t size_by_byte = std::get<2>(mem_read);
    uint64_t value = 0;
    for (int i = 0; i < size_by_byte; ++i) {
      value += (uint64_t)impl->load(address + i) << (i * 8);
    }
    Log("MemRead")
        .with("value", fmt::format("{:08X}", value))
        .with("address", fmt::format("{:08X}", address))
        .with("size_by_byte", size_by_byte)
        .info("spike detect mem read");
    for (size_t offset = 0; offset < size_by_byte; offset++) {
      uint8_t val_byte = ((uint64_t)value >> (offset * 8)) & 0xff;
      mem_access_record.all_reads[address + offset].reads.push_back(
          {.val = val_byte});
    }
  }
}

SpikeEvent::SpikeEvent(processor_t &proc, insn_fetch_t &fetch,
                       T1IPEmulator *impl,
                       // FIXME: dirty
                       size_t lsu_idx
                       )
    : proc(proc), impl(impl) {
  auto &xr = proc.get_state()->XPR;
  auto &fr = proc.get_state()->FPR;
  inst_bits = fetch.insn.bits();
  uint32_t opcode = clip(inst_bits, 0, 6);
  uint32_t width = clip(inst_bits, 12, 14); // also funct3
  uint32_t funct6 = clip(inst_bits, 26, 31);
  uint32_t mop = clip(inst_bits, 26, 27);
  uint32_t lumop = clip(inst_bits, 20, 24);
  uint32_t vm = clip(inst_bits, 25, 25);

  bool is_fp_operands = opcode == 0b1010111 && (width == 0b101 /* OPFVF */);
  if (is_fp_operands) {
    rs1_bits = extract_f32(fr[fetch.insn.rs1()]);
    rs2_bits = extract_f32(fr[fetch.insn.rs2()]);
  } else {
    rs1_bits = xr[fetch.insn.rs1()];
    rs2_bits = xr[fetch.insn.rs2()];
  }

  // only vfmv.f.s will write fp reg
  is_rd_fp = (opcode == 0b1010111) && (fetch.insn.rs1() == 0) &&
             (funct6 == 0b010000) && (vm == 1) && (width == 0b001);

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
  is_load = opcode == 0b0000111;
  is_store = opcode == 0b0100111;
  is_whole = mop == 0 && lumop == 8;
  is_widening = opcode == 0b1010111 && (funct6 >> 4) == 0b11;
  is_mask_vd =
      opcode == 0b1010111 && (funct6 >> 3 == 0b011 || funct6 == 0b010001);
  is_exit_insn = opcode == 0b1110011;
  is_vfence_insn = false;

  is_issued = false;
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
  for (auto &[addr, mem_write] : mem_access_record.all_writes) {
    CHECK_EQ(mem_write.num_completed_writes, mem_write.writes.size(),
             fmt::format(": [{}] expect to write mem {:08X}, not executed when "
                         "commit ({})",
                         impl->get_t(), addr, pc, describe_insn()));
  }
  for (auto &[addr, mem_read] : mem_access_record.all_reads) {
    CHECK_EQ(mem_read.num_completed_reads, mem_read.reads.size(),
             fmt::format(": [{}] expect to read mem {:08X}, not executed when "
                         "commit ({})",
                         impl->get_t(), addr, describe_insn()));
  }
  for (auto &[idx, vrf_write] : vrf_access_record.all_writes) {
    CHECK(vrf_write.executed,
          fmt::format(": [{}] expect to write vrf [{}][{}], not executed when "
                      "commit ({})",
                      impl->get_t(), idx / impl->config.vlen_in_bytes,
                      idx % impl->config.vlen_in_bytes, describe_insn()));
  }
}

void SpikeEvent::record_rd_write(const VRespInterface &v_resp) {
  // TODO: rtl should indicate whether resp_bits_data is valid
  if (is_rd_written) {
    CHECK_EQ(v_resp.data, rd_bits,
             fmt::format(": [{}] expect to write rd[{}] = {}, actual {}",
                         impl->get_t(), rd_idx, rd_bits, v_resp.data));
  }
}

std::pair<uint32_t, uint32_t> SpikeEvent::get_vrf_write_range() const {
  if (is_store) {
    return {0, 0}; // store will not write vrf
  } else if (is_load) {
    uint32_t vd_bytes_start = rd_idx * impl->config.vlen_in_bytes;
    if (is_whole) {
      return {vd_bytes_start, impl->config.vlen_in_bytes * (1 + vnf)};
    }
    uint32_t len = vlmul & 0b100
                       ? impl->config.vlen_in_bytes * (1 + vnf)
                       : impl->config.vlen_in_bytes * (1 + vnf) << vlmul;
    return {vd_bytes_start, len};
  } else {
    uint32_t vd_bytes_start = rd_idx * impl->config.vlen_in_bytes;

    if (is_mask_vd) {
      return {vd_bytes_start, impl->config.vlen_in_bytes};
    }

    uint32_t len = vlmul & 0b100 ? impl->config.vlen_in_bytes >> (8 - vlmul)
                                 : impl->config.vlen_in_bytes << vlmul;

    return {vd_bytes_start, is_widening ? len * 2 : len};
  }
}
#endif