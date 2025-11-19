#include <pokedex-sim_types.h>
#include <pokedex-sim_vars.h>
#include <pokedex-sim_exceptions.h>

#include <pokedex_interface.h>

#include <stdio.h>
#include <string.h>

// Working together with gcc flag "-fvisibility=hidden", only annotated symbols will be exported
#ifdef POKEDEX_DYLIB
 #define POKEDEX_EXPORT __attribute__((visibility("default")))
#else
 #define POKEDEX_EXPORT
#endif


// ASL compiled C model use global variables,
// therefore at most one model instance can exist in any time.
// We use a mutex to satisfy this constraint.
// The mutex should be locked/unlocked at model instance creation/destruction.
static int instance_mutex = 0;

// Each model instance should have their own callback data/vtable.
// However, ASL already uses global variables, so we follow it.
static void* cb_data = NULL;
static const struct pokedex_callback_vtable* cb_vtable = NULL;

///////////////////////
// callback wrappers //
///////////////////////

void FFI_inst_issue_0(uint32_t pc, uint32_t insn) {
    cb_vtable->log_inst_issue(cb_data, pc, insn);
}

void FFI_inst_issue_c_0(uint32_t pc, uint16_t insn) {
    cb_vtable->log_inst_issue_c(cb_data, pc, insn);
}

void FFI_inst_commit_0() {
    cb_vtable->log_inst_commit(cb_data);
}

void FFI_inst_xcpt_0(uint32_t xcause, uint32_t xtval) {
    cb_vtable->log_inst_xcpt(cb_data, xcause, xtval);
}

FFI_ReadResult_N_16 FFI_instruction_fetch_half_0(uint32_t pc) {
    uint16_t data = 0;
    int ret = cb_vtable->inst_fetch_2(cb_data, pc, &data);
    FFI_ReadResult_N_16 value = {
        .success = !ret,
        .data = data,
    };
    return value;
}

FFI_ReadResult_N_8 FFI_read_physical_memory_8bits_0(uint32_t addr) {
    uint8_t data = 0;
    int ret = cb_vtable->read_mem_1(cb_data, addr, &data);
    FFI_ReadResult_N_8 value = {
        .success = !ret,
        .data = data,
    };
    return value;
}

FFI_ReadResult_N_16 FFI_read_physical_memory_16bits_0(uint32_t addr) {
    uint16_t data = 0;
    int ret = cb_vtable->read_mem_2(cb_data, addr, &data);
    FFI_ReadResult_N_16 value = {
        .success = !ret,
        .data = data,
    };
    return value;
}

FFI_ReadResult_N_32 FFI_read_physical_memory_32bits_0(uint32_t addr) {
    uint32_t data = 0;
    int ret = cb_vtable->read_mem_4(cb_data, addr, &data);
    FFI_ReadResult_N_32 value = {
        .success = !ret,
        .data = data,
    };
    return value;
}

bool FFI_write_physical_memory_8bits_0(uint32_t addr, uint8_t data) {
    int ret = cb_vtable->write_mem_1(cb_data, addr, data);
    return !ret;
}

bool FFI_write_physical_memory_16bits_0(uint32_t addr, uint16_t data) {
    int ret = cb_vtable->write_mem_2(cb_data, addr, data);
    return !ret;
}

bool FFI_write_physical_memory_32bits_0(uint32_t addr, uint32_t data) {
    int ret = cb_vtable->write_mem_4(cb_data, addr, data);
    return !ret;
}

FFI_ReadResult_N_32 FFI_amo_0(AmoOperationType operation, uint32_t addr, uint32_t value) {
    uint8_t opcode = 0;
    switch (operation) {
        case AMO_SWAP: opcode = POKEDEX_AMO_SWAP; break;
        case AMO_ADD: opcode = POKEDEX_AMO_ADD; break;
        case AMO_AND: opcode = POKEDEX_AMO_AND; break;
        case AMO_OR: opcode = POKEDEX_AMO_OR; break;
        case AMO_XOR: opcode = POKEDEX_AMO_XOR; break;
        case AMO_MAX: opcode = POKEDEX_AMO_MAX; break;
        case AMO_MIN: opcode = POKEDEX_AMO_MIN; break;
        case AMO_MAXU: opcode = POKEDEX_AMO_MAXU; break;
        case AMO_MINU: opcode = POKEDEX_AMO_MINU; break;
        default: assert(false && "unknown AMO type");
    }
    uint32_t data;
    int ret = cb_vtable->amo_mem_4(cb_data, addr, opcode, value, &data);
    FFI_ReadResult_N_32 ret_value = {
        .success = !ret,
        .data = data,
    };
    return ret_value;
}

FFI_ReadResult_N_32 FFI_load_reserved_0(uint32_t addr) {
    assert(false && "TODO: lr");
}

bool FFI_store_conditional_0(uint32_t addr, uint32_t data) {
    assert(false && "TODO: sc");
}

unsigned _BitInt(1) FFI_machine_time_interrupt_pending_0() {
    // TODO
    return 0;
}

unsigned _BitInt(1) FFI_machine_external_interrupt_pending_0() {
    // TODO
    return 0;
}

void FFI_write_GPR_hook_0(signed _BitInt(6) rd, uint32_t data) {
    cb_vtable->log_write_xreg(cb_data, rd, data);
}

void FFI_write_FPR_hook_0(signed _BitInt(6) fd, uint32_t data) {
    cb_vtable->log_write_freg(cb_data, fd, data);
}

void FFI_write_VREG_hook_0(signed _BitInt(6) vd, unsigned _BitInt(256) data) {
    const int VLENB = 32;
    uint8_t data_bytes[VLENB];
    memcpy(data_bytes, &data, VLENB);
    cb_vtable->log_write_vreg(cb_data, vd, data_bytes, VLENB);
}

void FFI_write_CSR_hook_0(const char *name, uint32_t value) {
    cb_vtable->log_write_csr(cb_data, name, value);
}

void FFI_debug_print_0(const char* s) {
    cb_vtable->debug_print(cb_data, s);
}

void FFI_debug_unimpl_insn_0(const char *name, uint32_t data) {
    const int BUFLEN = 256;
    char message[BUFLEN];
    snprintf(message, BUFLEN, "unimplemented instruction \"%s\", bits=0x%08x", name, data);
    cb_vtable->debug_print(cb_data, message);
}

void FFI_debug_unimpl_insn_c_0(const char *name, uint16_t data) {
    const int BUFLEN = 256;
    char message[BUFLEN];
    snprintf(message, BUFLEN, "unimplemented instruction \"%s\", bits=0x%08x (compressed)", name, data);
    cb_vtable->debug_print(cb_data, message);
}


////////////////////
// vtable methods //
////////////////////

static void* pokedex_create(
    const struct pokedex_create_info* info,
    char* err_buf,
    size_t buflen
) {
    if (instance_mutex != 0) {
        snprintf(err_buf, buflen, "ASL model is already created but not destroyed");
        return NULL;
    }

    instance_mutex = 1;

    cb_data = info->callback_data;
    cb_vtable = info->callback_vtable;

    ASL_ResetConfigAndState_0();

    // return something random non-null to indicate success
    return &instance_mutex;
}

static void pokedex_destroy(void* _model) {
    if (_model) {
        instance_mutex = 0;
    }
}

static void pokedex_reset(void* _model, uint32_t initial_pc) {
    (void)(_model);

    ASL_ResetState_0();
    PC_write_0(initial_pc);
}

static void pokedex_step(void* _model) {
    (void)(_model);

    ASL_Step_0();
}

static struct pokedex_vtable vtable = {
    .abi_version = POKEDEX_ABI_VERSION,
    .model_isa = "rv32imacf_zve32_zvl256",
    .create = pokedex_create,
    .destroy = pokedex_destroy,

    .reset = pokedex_reset,
    .step = pokedex_step,
};


POKEDEX_EXPORT const struct pokedex_vtable* EXPORT_ASL_MODEL_get_pokedex_vtable() {
  return &vtable;
}
