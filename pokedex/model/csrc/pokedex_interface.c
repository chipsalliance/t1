#include <pokedex-sim_types.h>
#include <pokedex-sim_vars.h>
#include <pokedex-sim_exceptions.h>

#include <pokedex_interface.h>

#include <stdio.h>
#include <string.h>
#include <stdatomic.h>

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
static atomic_flag instance_mutex = ATOMIC_FLAG_INIT;

// Each model instance should have their own callback data/vtable.
// However, ASL already uses global variables, so we follow it.

// if it is NULL, debug log will be silently ignored
static void (*cb_debug_log)(const char* message);

static bool debug_inst_issue;

static void* mem_cb_data = NULL;
static const struct pokedex_mem_callback_vtable* mem_cb_vtable = NULL;

static struct pokedex_trace_buffer trace_buffer;

///////////////////////
// callback wrappers //
///////////////////////



FFI_ReadResult_N_16 FFI_instruction_fetch_half_0(uint32_t pc) {
    uint16_t data = 0;
    int ret = mem_cb_vtable->inst_fetch_2(mem_cb_data, pc, &data);
    FFI_ReadResult_N_16 value = {
        .success = !ret,
        .data = data,
    };
    return value;
}

FFI_ReadResult_N_8 FFI_read_physical_memory_8bits_0(uint32_t addr) {
    uint8_t data = 0;
    int ret = mem_cb_vtable->read_mem_1(mem_cb_data, addr, &data);
    FFI_ReadResult_N_8 value = {
        .success = !ret,
        .data = data,
    };
    return value;
}

FFI_ReadResult_N_16 FFI_read_physical_memory_16bits_0(uint32_t addr) {
    uint16_t data = 0;
    int ret = mem_cb_vtable->read_mem_2(mem_cb_data, addr, &data);
    FFI_ReadResult_N_16 value = {
        .success = !ret,
        .data = data,
    };
    return value;
}

FFI_ReadResult_N_32 FFI_read_physical_memory_32bits_0(uint32_t addr) {
    uint32_t data = 0;
    int ret = mem_cb_vtable->read_mem_4(mem_cb_data, addr, &data);
    FFI_ReadResult_N_32 value = {
        .success = !ret,
        .data = data,
    };
    return value;
}

bool FFI_write_physical_memory_8bits_0(uint32_t addr, uint8_t data) {
    int ret = mem_cb_vtable->write_mem_1(mem_cb_data, addr, data);
    return !ret;
}

bool FFI_write_physical_memory_16bits_0(uint32_t addr, uint16_t data) {
    int ret = mem_cb_vtable->write_mem_2(mem_cb_data, addr, data);
    return !ret;
}

bool FFI_write_physical_memory_32bits_0(uint32_t addr, uint32_t data) {
    int ret = mem_cb_vtable->write_mem_4(mem_cb_data, addr, data);
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
    int ret = mem_cb_vtable->amo_mem_4(mem_cb_data, addr, opcode, value, &data);
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

void FFI_write_GPR_hook_0(unsigned _BitInt(5) rd) {
    assert(trace_buffer.valid);
    trace_buffer.xreg_mask |= 1 << rd;
}

void FFI_write_FPR_hook_0(unsigned _BitInt(5) fd) {
    assert(trace_buffer.valid);
    trace_buffer.freg_mask |= 1 << fd;
}

void FFI_write_VREG_hook_0(uint32_t vd_mask) {
    assert(trace_buffer.valid);
    trace_buffer.vreg_mask |= vd_mask;
}

void FFI_write_CSR_hook_0(unsigned _BitInt(12) csr) {
    assert(trace_buffer.valid);

    assert(trace_buffer.csr_count < POKEDEX_MAX_CSR_WRITE);
    trace_buffer.csr_indices[trace_buffer.csr_count++] = csr;
}

void FFI_debug_print_0(const char* s) {
    if (cb_debug_log) {
        cb_debug_log(s);
    }
}

void FFI_debug_unimpl_insn_0(const char *name, uint32_t data) {
    if (cb_debug_log) {
        const int BUFLEN = 256;
        char message[BUFLEN];
        snprintf(message, BUFLEN, "unimplemented instruction \"%s\", bits=0x%08x", name, data);
        cb_debug_log(message);
    }
}

void FFI_debug_unimpl_insn_c_0(const char *name, uint16_t data) {
    if (cb_debug_log) {
        const int BUFLEN = 256;
        char message[BUFLEN];
        snprintf(message, BUFLEN, "unimplemented instruction \"%s\", bits=0x%08x (compressed)", name, data);
        cb_debug_log(message);
    }
}

void FFI_debug_issue_0(uint32_t pc, uint32_t insn) {
    if (cb_debug_log && debug_inst_issue) {
        const int BUFLEN = 256;
        char message[BUFLEN];
        snprintf(message, BUFLEN, "inst issue: pc=0x%08x, inst=0x%08x", pc, insn);
        cb_debug_log(message);
    }
}

void FFI_debug_issue_c_0(uint32_t pc, uint16_t insn) {
    if (cb_debug_log && debug_inst_issue) {
        const int BUFLEN = 256;
        char message[BUFLEN];
        snprintf(message, BUFLEN, "inst issue: pc=0x%08x, inst=0x%04x (compressed)", pc, insn);
        cb_debug_log(message);
    }
}

void FFI_debug_unsupported_csr_0(unsigned _BitInt(12) csr) {
    if (cb_debug_log) {
        const int BUFLEN = 256;
        char message[BUFLEN];
        snprintf(message, BUFLEN, "unsupported csr debugger read: csr=0x%03x", (uint32_t)csr);
        cb_debug_log(message);
    }
}

////////////////////
// vtable methods //
////////////////////

static void* model_create(
    const struct pokedex_create_info* info,
    char* err_buf,
    size_t buflen
) {
    if (atomic_flag_test_and_set_explicit(&instance_mutex, memory_order_acq_rel)) {
        snprintf(err_buf, buflen, "ASL model is already created but not destroyed");
        return NULL;
    }

    cb_debug_log = info->debug_log;
    trace_buffer.valid = 0;
    ASL_ResetConfigAndState_0();

    // return something random non-null to indicate success
    return &instance_mutex;
}

static void model_destroy(void* _model) {
    if (_model) {
        atomic_flag_clear_explicit(&instance_mutex, memory_order_release);
    }
}

static void model_reset(void* _model, uint32_t initial_pc) {
    (void)(_model);

    trace_buffer.valid = 0;
    ASL_ResetState_0();
    PC_write_0(initial_pc);
}

static uint8_t model_step_trace(
    void* _model,
    const struct pokedex_mem_callback_vtable* mem_callback_vtable,
    void* mem_callback_data
) {
    (void)(_model);

    mem_cb_vtable = mem_callback_vtable;
    mem_cb_data = mem_callback_data;

    memset(&trace_buffer, 0, sizeof(trace_buffer));
    trace_buffer.valid  = 1;
    trace_buffer.pc = PC_read_0();

    FFI_StepResult result = ASL_Step_0();

    trace_buffer.step_status = result.code;
    trace_buffer.inst = result.inst;

    mem_cb_vtable = NULL;
    mem_cb_data = NULL;

    return result.code;
}

const struct pokedex_trace_buffer* model_get_trace_buffer(void* _model) {
    (void)(_model);

    return &trace_buffer;
}

static uint64_t sext(uint32_t x) {
    return (uint64_t)(int64_t)(int32_t)x;
}

static uint64_t nanbox(uint32_t x) {
    return 0xFFFFFFFF00000000 | x;
}

static void model_read_pc(void* _model, uint64_t* ret){
    (void)(_model);

    *ret = sext(ASL_read_PC_0());
}

static void model_read_xreg(void* _model, uint8_t xs, uint64_t* ret){
    (void)(_model);

    *ret = sext(ASL_read_XREG_0(xs));
}

static void model_read_freg(void* _model, uint8_t fs, uint64_t* ret){
    (void)(_model);

    *ret = nanbox(ASL_read_FREG_0(fs));
}

static void model_read_vreg(void* _model, uint8_t vs, uint8_t* buf, size_t buflen) {
    (void)(_model);

    // FIXME: get VLEN from ASL side instead of hard-coding
    const int VLEN = 256;

    assert(buflen == VLEN / 8);

    unsigned _BitInt(256) vreg = ASL_read_VREG_0(vs);
    memcpy(buf, &vreg, VLEN / 8);
}

static void model_read_csr(void* _model, uint16_t csr, uint64_t* ret){
    (void)(_model);

    // zero extension
    *ret = ASL_read_CSR_0(csr);
}

static const struct pokedex_model_description model_desc = {
    .model_isa = "rv32imacf_zve32_zvl256",
    .xlen = 32,
    .flen = 32,
    .vlen = 256,
};

const struct pokedex_model_description* model_get_description(void* _model) {
    (void)(_model);

    return &model_desc;
}

static const struct pokedex_model_export model_export = {
    .abi_version = POKEDEX_ABI_VERSION,
    .create = model_create,
    .destroy = model_destroy,

    .get_description = model_get_description,

    .reset = model_reset,
    .step = model_step_trace,
    .step_trace = model_step_trace,
    .get_trace_buffer = model_get_trace_buffer,

    .get_pc = model_read_pc,
    .get_xreg = model_read_xreg,
    .get_freg = model_read_freg,
    .get_vreg = model_read_vreg,
    .get_csr = model_read_csr,
};

POKEDEX_EXPORT const struct pokedex_model_export* EXPORT_pokedex_get_model_export() {
  return &model_export;
}
