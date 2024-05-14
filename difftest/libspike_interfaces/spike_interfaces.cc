#include "spike_interfaces.h"

constexpr uint32_t CSR_MSIMEND = 0x7cc;

cfg_t make_spike_cfg(const std::string& varch) {
  cfg_t cfg;
  cfg.initrd_bounds = std::make_pair((reg_t)0, (reg_t)0),
  cfg.bootargs = nullptr;
  cfg.isa = DEFAULT_ISA;
  cfg.priv = DEFAULT_PRIV;
  cfg.varch = varch.data();
  cfg.misaligned = false;
  cfg.endianness = endianness_little;
  cfg.pmpregions = 16;
  cfg.pmpgranularity = 4;
  cfg.mem_layout = std::vector<mem_cfg_t>();
  cfg.hartids = std::vector<size_t>();
  cfg.explicit_hartids = false;
  cfg.real_time_clint = false;
  cfg.trigger_count = 4;
  return cfg;
}

Spike::Spike(const char* arch, const char* set, const char* lvl)
    : sim(),
      varch(arch),
      isa(set, lvl),
      cfg(make_spike_cfg(varch)),
      proc(
          /*isa*/ &isa,
          /*cfg*/ &cfg,
          /*sim*/ &sim,
          /*id*/ 0,
          /*halt on reset*/ true,
          /*log_file_t*/ nullptr,
          /*sout*/ std::cerr) {
  auto& csrmap = proc.get_state()->csrmap;
  csrmap[CSR_MSIMEND] = std::make_shared<basic_csr_t>(&proc, CSR_MSIMEND, 1);
  proc.enable_log_commits();
}

spike_t* spike_new(const char* arch, const char* set, const char* lvl) {
  return new spike_t{new Spike(arch, set, lvl)};
}

const char* proc_disassemble(spike_processor_t* proc) {
  auto pc = proc->p->get_state()->pc;
  auto mmu = proc->p->get_mmu();
  auto disasm = proc->p->get_disassembler();
  auto fetch = mmu->load_insn(pc);
  return strdup(disasm->disassemble(fetch.insn).c_str());
}

spike_processor_t* spike_get_proc(spike_t* spike) {
  return new spike_processor_t{spike->s->get_proc()};
}

void proc_reset(spike_processor_t* proc) {
  proc->p->reset();
}

spike_state_t* proc_get_state(spike_processor_t* proc) {
  return new spike_state_t{proc->p->get_state()};
}

reg_t proc_func(spike_processor_t* proc) {
  auto pc = proc->p->get_state()->pc;
  auto mmu = proc->p->get_mmu();
  auto fetch = mmu->load_insn(pc);
  return fetch.func(proc->p, fetch.insn, pc);
}

reg_t proc_get_insn(spike_processor_t* proc) {
  auto pc = proc->p->get_state()->pc;
  auto mmu = proc->p->get_mmu();
  auto fetch = mmu->load_insn(pc);
  return fetch.insn.bits();
}

uint8_t* proc_get_vreg_addr(spike_processor_t* proc) {
  return &proc->p->VU.elt<uint8_t>(0, 0);
}

uint32_t extract_f32(freg_t f) { return (uint32_t)f.v[0]; }

inline uint32_t clip(uint32_t binary, int a, int b) {
  int nbits = b - a + 1;
  uint32_t mask = nbits >= 32 ? (uint32_t)-1 : (1 << nbits) - 1;
  return (binary >> a) & mask;
}

uint64_t proc_get_rs(spike_processor_t* proc) {
  auto pc = proc->p->get_state()->pc;
  auto fetch = proc->p->get_mmu()->load_insn(pc);
  return (uint64_t)fetch.insn.rs1() << 32 | (uint64_t)fetch.insn.rs2();
}

uint32_t proc_get_rd(spike_processor_t* proc) {
  auto pc = proc->p->get_state()->pc;
  auto fetch = proc->p->get_mmu()->load_insn(pc);
  return fetch.insn.rd();
}

uint64_t proc_get_rs_bits(spike_processor_t* proc) {
  auto state = proc->p->get_state();
  auto &xr = state->XPR;
  auto &fr = state->FPR;
  auto pc = state->pc;
  auto inst_bits = proc_get_insn(proc);

  uint32_t opcode = clip(inst_bits, 0, 6);
  uint32_t width = clip(inst_bits, 12, 14); // also funct3
  auto fetch = proc->p->get_mmu()->load_insn(pc);
  uint32_t rs1_bits, rs2_bits;
  bool is_fp_operands = opcode == 0b1010111 && (width == 0b101 /* OPFVF */);
  if (is_fp_operands) {
    rs1_bits = extract_f32(fr[fetch.insn.rs1()]);
    rs2_bits = extract_f32(fr[fetch.insn.rs2()]);
  } else {
    rs1_bits = xr[fetch.insn.rs1()];
    rs2_bits = xr[fetch.insn.rs2()];
  }

  return (uint64_t)rs1_bits << 32 | (uint64_t)rs2_bits;
}

uint64_t proc_vu_get_vtype(spike_processor_t* proc) {
  return proc->p->VU.vtype->read();
}

uint32_t proc_vu_get_vxrm(spike_processor_t* proc) {
  return proc->p->VU.vxrm->read();
}

uint32_t proc_vu_get_vnf(spike_processor_t* proc) {
  auto pc = proc->p->get_state()->pc;
  auto fetch = proc->p->get_mmu()->load_insn(pc);
  return fetch.insn.v_nf();
}

bool proc_vu_get_vill(spike_processor_t* proc) {
  return proc->p->VU.vill;
}

bool proc_vu_get_vxsat(spike_processor_t* proc) {
  return proc->p->VU.vxsat->read();
}

uint32_t proc_vu_get_vl(spike_processor_t* proc) {
  return proc->p->VU.vl->read();
}

uint16_t proc_vu_get_vstart(spike_processor_t* proc) {
  return proc->p->VU.vstart->read();
}

reg_t state_get_pc(spike_state_t* state) {
  return state->s->pc;
}

void state_clear(spike_state_t* state) {
  state->s->log_reg_write.clear();
  state->s->log_mem_read.clear();
  state->s->log_mem_write.clear();
}

static void state_set_serialized(spike_state_t* state, bool serialized) {
  state->s->serialized = serialized;
}

uint64_t state_handle_pc(spike_state_t* state, uint64_t new_pc) {
  if ((new_pc & 1) == 0) {
    state_set_pc(state, new_pc);
  } else {
    switch (new_pc) {
      case PC_SERIALIZE_BEFORE:
        state_set_serialized(state, true);
        break;
      case PC_SERIALIZE_AFTER:
        break;
      default:
        return -1;
    }
  }
  return 0;
}

void state_set_pc(spike_state_t* state, uint64_t pc) {
  state->s->pc = pc;
}

uint32_t state_get_mem_write_size(spike_state_t* state) {
  return state->s->log_mem_write.size();
}

uint32_t state_get_mem_write_addr(spike_state_t* state, uint32_t index) {
  return std::get<0>(state->s->log_mem_write[index]) & 0xffffffff;
}

uint64_t state_get_mem_write_value(spike_state_t* state, uint32_t index) {
  return std::get<1>(state->s->log_mem_write[index]);
}

uint8_t state_get_mem_write_size_by_byte(spike_state_t* state, uint32_t index) {
  return std::get<2>(state->s->log_mem_write[index]);
}

uint32_t state_get_mem_read_size(spike_state_t* state) {
  return state->s->log_mem_read.size();
}

uint32_t state_get_mem_read_addr(spike_state_t* state, uint32_t index) {
  return std::get<0>(state->s->log_mem_read[index]) & 0xffffffff;
}

uint8_t state_get_mem_read_size_by_byte(spike_state_t* state, uint32_t index) {
  return std::get<2>(state->s->log_mem_read[index]);
}

reg_t state_exit(spike_state_t* state) {
  auto& csrmap = state->s->csrmap;
  return csrmap[CSR_MSIMEND]->read();
}

void spike_register_callback(ffi_callback callback) {
  ffi_addr_to_mem = callback;

  return;
}

void spike_destruct(spike_t* spike) {
  delete spike;
}

void proc_destruct(spike_processor_t* proc) {
  delete proc;
}

void state_destruct(spike_state_t* state) {
  delete state;
}
