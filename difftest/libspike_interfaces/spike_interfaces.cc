#include "spike_interfaces.h"

constexpr uint32_t CSR_MSIMEND = 0x7cc;

cfg_t make_spike_cfg(const std::string &varch) {
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

const char* proc_disassemble(spike_processor_t* proc, reg_t pc) {
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

reg_t proc_func(spike_processor_t* proc, reg_t pc) {
  auto mmu = proc->p->get_mmu();
  auto fetch = mmu->load_insn(pc);
  return fetch.func(proc->p, fetch.insn, pc);
}

reg_t proc_get_insn(spike_processor_t* proc, reg_t pc) {
  auto mmu = proc->p->get_mmu();
  auto fetch = mmu->load_insn(pc);
  return fetch.insn.bits();
}

reg_t state_get_pc(spike_state_t* state) {
  return state->s->pc;
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


void destruct(void* ptr) {
  if (ptr == nullptr)
    return;
  delete ptr;
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
