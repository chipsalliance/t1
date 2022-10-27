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

// 记录的数据会不会是之前多条指令的结果
void SpikeEvent::log() {
  state_t *state = proc.get_state();
  commit_log_reg_t &regs = state->log_reg_write;
  commit_log_mem_t &loads = state->log_mem_read;
  commit_log_mem_t &stores = state->log_mem_write;
  mem_read_info = loads;
  size_t load_size = loads.size();
  size_t store_size = stores.size();
  if (!state->log_mem_read.empty()) {
    //std::vector <std::tuple<int,int>> ve = {std::make_tuple(1,1),std::make_tuple(2,2)};
    //LOG(INFO) << fmt::format(" test = {}", ve);
    LOG(INFO) << fmt::format(" load times = {}", load_size);
    //LOG(INFO) << fmt::format(" front reg = {}", std::get<0>(loads.front()));
    //LOG(INFO) << fmt::format(" front address = {}", std::get<1>(loads.front()));
    //LOG(INFO) << fmt::format(" front size = {}", std::get<2>(loads.front()));
    for (const auto item: loads) {
      //std::get<1> (item) = 1;
      //LOG(INFO) << fmt::format(" load addr, value, size = {}, {}, {}", std::get<0>(item),std::get<1>(item),std::get<2>(item));
      uint64_t addr = std::get<0>(item);
      uint64_t value = mem_load(std::get<0>(item), std::get<2>(item));
      uint8_t size = std::get<2>(item);
      LOG(INFO) << fmt::format(" load addr, load back value, size = {:X}, {}, {}", addr, value, size);
      log_mem_queue.push_back({addr, value, size});
    }
  }
  if (!state->log_mem_write.empty()) {
    LOG(INFO) << fmt::format(" store size = {}", store_size);
  }

  for (auto reg: regs) {
    // in spike, log_reg_write is arrange:
    // xx0000 <- x
    // xx0001 <- f
    // xx0010 <- vreg
    // xx0011 <- vec
    // xx0100 <- csr
    if ((reg.first & 0xf) == 2) {
      // TODO: based on VLMUL, SEW, set _vrf
      continue;
    }
  }
  for (auto mem_write: state->log_mem_write) {

  }
  for (auto mem_read: state->log_mem_write) {

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
