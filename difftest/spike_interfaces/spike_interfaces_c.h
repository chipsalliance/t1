#ifndef __SPIKE_INTERFCES_C_H__
#define __SPIKE_INTERFCES_C_H__

#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef char *(*ffi_callback)(void *, uint64_t);

typedef struct spike_t spike_t;
typedef struct spike_processor_t spike_processor_t;
typedef struct spike_state_t spike_state_t;

void spike_register_callback(void *ffi_target, ffi_callback callback);
spike_t *spike_new(const char *set, const char *lvl,
                   size_t lane_number);
const char *proc_disassemble(spike_processor_t *proc);
void proc_reset(spike_processor_t *proc);
spike_processor_t *spike_get_proc(spike_t *spike);
spike_state_t *proc_get_state(spike_processor_t *proc);

uint64_t proc_func(spike_processor_t *proc);
uint64_t proc_get_insn(spike_processor_t *proc);
uint8_t proc_get_vreg_data(spike_processor_t *proc, uint32_t vreg_idx,
                           uint32_t vreg_offset);
uint32_t proc_get_rs1(spike_processor_t *proc);
uint32_t proc_get_rs2(spike_processor_t *proc);
uint32_t proc_get_rd(spike_processor_t *proc);

uint64_t proc_vu_get_vtype(spike_processor_t *proc);
uint32_t proc_vu_get_vxrm(spike_processor_t *proc);
uint32_t proc_vu_get_vnf(spike_processor_t *proc);
bool proc_vu_get_vill(spike_processor_t *proc);
bool proc_vu_get_vxsat(spike_processor_t *proc);
uint32_t proc_vu_get_vl(spike_processor_t *proc);
uint16_t proc_vu_get_vstart(spike_processor_t *proc);

uint64_t state_get_pc(spike_state_t *state);
uint64_t state_handle_pc(spike_state_t *state, uint64_t new_pc);
void state_set_pc(spike_state_t *state, uint64_t pc);
uint32_t state_get_reg(spike_state_t *state, uint32_t index, bool is_fp);
uint32_t state_get_reg_write_size(spike_state_t *state);
uint32_t state_get_reg_write_index(spike_state_t *state, uint32_t index);
uint32_t state_get_mem_write_size(spike_state_t *state);
uint32_t state_get_mem_write_addr(spike_state_t *state, uint32_t index);
uint64_t state_get_mem_write_value(spike_state_t *state, uint32_t index);
uint8_t state_get_mem_write_size_by_byte(spike_state_t *state, uint32_t index);
uint32_t state_get_mem_read_size(spike_state_t *state);
uint32_t state_get_mem_read_addr(spike_state_t *state, uint32_t index);
uint8_t state_get_mem_read_size_by_byte(spike_state_t *state, uint32_t index);
void state_set_mcycle(spike_state_t *state, size_t mcycle);
void state_clear(spike_state_t *state);

void spike_destruct(spike_t *spike);
void proc_destruct(spike_processor_t *proc);
void state_destruct(spike_state_t *state);

#ifdef __cplusplus
}
#endif

#endif // __SPIKE_INTERFCES_C_H__
