#pragma once
#ifndef _POKEDEX_INTERFACE_H
#define _POKEDEX_INTERFACE_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define POKEDEX_ABI_VERSION "2025-12-09"

#define POKEDEX_AMO_SWAP 0
#define POKEDEX_AMO_ADD 1
#define POKEDEX_AMO_AND 2
#define POKEDEX_AMO_OR 3
#define POKEDEX_AMO_XOR 4
#define POKEDEX_AMO_MAX 5
#define POKEDEX_AMO_MIN 6
#define POKEDEX_AMO_MAXU 7
#define POKEDEX_AMO_MINU 8 

// exception during instruction fetch 
#define POKEDEX_STEP_RESULT_FETCH_XCPT 1

// exception in decoding/excuting a non-compressed instruction
#define POKEDEX_STEP_RESULT_INST_EXCEPTION 2

// non-compressed instruction commits
#define POKEDEX_STEP_RESULT_INST_COMMIT 4 

// exception in decoding/excuting a compressed instruction
#define POKEDEX_STEP_RESULT_INST_C_EXCEPTION 10

// exception in decoding/excuting a compressed instruction
#define POKEDEX_STEP_RESULT_INST_C_COMMIT 12

// interrupt happens
#define POKEDEX_STEP_RESULT_INTERRUPT 16

struct pokedex_mem_callback_vtable;

struct pokedex_create_info {
    // print some diagnotic-only meesage
    // if it is NULL, debug log will be silently ignored
    void (*debug_log)(const char* message);

    // following debug options only effectful if debug_log is not NULL

    uint8_t debug_inst_issue;
};

struct pokedex_model_description {
    // informationaly only
    const char* model_isa;

    // supported privilege string.
    const char* model_priv;

    // valid value: 32, 64
    uint32_t xlen;

    // valid value: 0, 32, 64
    uint32_t flen;

    // 0 means V is not supported
    uint32_t vlen;

    // TODO: supported CSRs
};

struct pokedex_model_export {
    // abi_version must be the first field
    const char* abi_version;

    // Parameter"info" must be non-null 
    //
    // If success, return a non-null opaque pointer that represents model data.
    // NULL indicates failure, and the callee may additionally 
    // write error message to err_buf (may use snprintf for safety)
    void* (*create)(const struct pokedex_create_info* info, char* err_buf, size_t err_buflen);

    // If model is NULL, it is a no-op
    void (*destroy)(void* model);

    // Following methods require non-null model pointer

    const struct pokedex_model_description* (*get_description)(void* model);

    // This is a mutable operation.
    void (*reset)(void* model, uint32_t intitial_pc);
    
    // This is a mutable operation.
    // See POKEDEX_STEP_RESULT_XXX macros for its return value.
    // It may or may not record state write traces.
    // NOTE: step_trace is always an valid implementation of step
    uint8_t (*step)(
        void* model,
        const struct pokedex_mem_callback_vtable* mem_callback_vtable,
        void* mem_callback_data
    );

    // This is a mutable operation.
    // It always record state write traces,
    // which may be accessed by get_trace_buffer.
    uint8_t (*step_trace)(
        void* model,
        const struct pokedex_mem_callback_vtable* mem_callback_vtable,
        void* mem_callback_data
    );

    // Returns a pointer to valid trace buffer if and only if
    // the last operation is an step_trace (or a step actually traces).
    // Otherwise, returns either NULL or invalid.
    //
    // The lifetime of trace buffer is managed by the model.
    // Any mutation operation will invalidate all existing trace buffer pointers.
    // NOTE: precisely "fn get_trace_buffer<'a>(&'a self) -> Option<&'a TraceBuffer>" in Rust
    const struct pokedex_trace_buffer* (*get_trace_buffer)(void* model);

    // Following methods are debugger accessors.
    // They guarantee do not have any side effects
    // The caller is responsible to provide valid arugments,
    // by probing features through get_description

    // when XLEN=32, upper bits of ret are unspecified
    void (*get_pc)(void* model, uint64_t* ret);

    // 0 <= xs <= 31
    // when XLEN=32, upper bits of ret are unspecified
    void (*get_xreg)(void* model, uint8_t xs, uint64_t* ret);

    // 0 <= fs <= 31, only callable when FLEN > 0
    // when FLEN=32, upper bits of ret are unspecified
    void (*get_freg)(void* model, uint8_t fs, uint64_t* ret);

    // 0 <= vs <= 31, only callable when VLEN > 0
    // buflen must be precisely VLEN/8
    void (*get_vreg)(void* model, uint8_t vs, uint8_t* buf, size_t buflen);

    // 0 <= csr <= 0xFFF, and csr must be an supported csr
    // when XLEN=32, upper bits of ret are unspecifieds
    void (*get_csr)(void* model, uint16_t csr, uint64_t* ret);
};

struct pokedex_mem_callback_vtable {
    // All memory operations return 0 in sucess, return non-zero in failure.
    // currently we only return 1 for access fault in failure.
    //
    // All memory operations require aligned addresses.
    //

    int (*inst_fetch_2)(void* cb_data, uint32_t addr, uint16_t* ret);
    int (*read_mem_1)(void* cb_data, uint32_t addr, uint8_t* ret);
    int (*read_mem_2)(void* cb_data, uint32_t addr, uint16_t* ret);
    int (*read_mem_4)(void* cb_data, uint32_t addr, uint32_t* ret);
    int (*write_mem_1)(void* cb_data, uint32_t addr, uint8_t value);
    int (*write_mem_2)(void* cb_data, uint32_t addr, uint16_t value);
    int (*write_mem_4)(void* cb_data, uint32_t addr, uint32_t value);
    int (*amo_mem_4)(void* cb_data, uint32_t addr, uint8_t amo_op, uint32_t value, uint32_t* ret);
    int (*lr_mem_4)(void* cb_data, uint32_t addr, uint32_t* ret);
    int (*sc_mem_4)(void* cb_data, uint32_t addr, uint32_t value, uint32_t* ret);
};

#define POKEDEX_MAX_CSR_WRITE 16

// Record which registers may be written during step_trace.
// The record is conservative, it may contain registers whose value actually does not change.
// It does not record the written values, use get_xxx to retrieve them.
struct pokedex_trace_buffer {
    uint8_t valid;

    // return value of last step
    uint8_t step_status;

    uint8_t csr_count;
    uint8_t reserved;

    // pc at the start of last step
    uint32_t pc;

    // issued instruction of last step
    // NOTE: step_status will tell you whether is compressed
    uint32_t inst;

    uint32_t xreg_mask;
    uint32_t freg_mask;
    uint32_t vreg_mask;
    uint16_t csr_indices[POKEDEX_MAX_CSR_WRITE];
};

typedef const struct pokedex_model_export* (*pokedex_get_model_export_t)();

// Example:
//
// static const struct pokedex_model_export model_export = {
//     .abi_version = POKEDEX_ABI_VERSION,
//     .create = ...,
//     .destroy = ...,
//
//     ...
// };
// 
// // This is the only required export symbol
// const struct pokedex_model_export* EXPORT_pokedex_get_model_export() {
//   return &model_export;
// }

#ifdef __cplusplus
}
#endif

#endif // include guard
