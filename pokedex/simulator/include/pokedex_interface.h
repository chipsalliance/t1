#pragma once
#ifndef _POKEDEX_INTERFACE_H
#define _POKEDEX_INTERFACE_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define POKEDEX_ABI_VERSION "2025-11-12"

#define POKEDEX_AMO_SWAP 0
#define POKEDEX_AMO_ADD 1
#define POKEDEX_AMO_AND 2
#define POKEDEX_AMO_OR 3
#define POKEDEX_AMO_XOR 4
#define POKEDEX_AMO_MAX 5
#define POKEDEX_AMO_MIN 6
#define POKEDEX_AMO_MAXU 7
#define POKEDEX_AMO_MINU 8

struct pokedex_callback_vtable;

struct pokedex_create_info {
    void* callback_data;
    const struct pokedex_callback_vtable* callback_vtable;
};

struct pokedex_vtable {
    // abi_version must be the first field
    const char* abi_version;

    // a non-null string to describe the isa
    // currently it is informational only
    const char* model_isa;

    // Parameter"info" must be non-null 
    //
    // If success, return a non-null opaque pointer that represents model data.
    // NULL indicates failure, and the callee may additionally 
    // write error message to err_buf (may use snprintf for safety)
    void* (*create)(const struct pokedex_create_info* info, char* err_buf, size_t err_buflen);

    // If model is NULL, it is a no-op
    void (*destroy)(void* model);

    // Following methods require non-null model pointer

    void (*reset)(void* model, uint32_t intitial_pc);
    void (*step)(void* model);
};

struct pokedex_callback_vtable {
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

    // inst_issue must be terminated with eaxtly one of inst_commit or inst_xcpt.
    // All state-write logs must happen between inst_issue and its pairing inst_commit/inst_xcpt
    void (*log_inst_issue)(void* cb_data, uint32_t issue, uint32_t inst);

    // Same as inst_issue but for compressed instruction
    void (*log_inst_issue_c)(void* cb_data, uint32_t issue, uint16_t inst);

    // Must pair with the previous inst_issue
    void (*log_inst_commit)(void* cb_data);

    // Must pair with the previous inst_issue
    void (*log_inst_xcpt)(void* cb_data, uint32_t xcause, uint32_t xtval);

    // Indicate an interrupt is taken.
    // intr_taken can not appear inside the paring of issue and inst_commit/inst_xcpt
    //
    // xepc: records where it takes, coincides with the value written to xepc
    // intr_code: interrupt number, exactly xcause with leading bit cleared.
    void (*log_intr_taken)(void* cb_data, uint32_t xepc, uint32_t intr_code);

    // 1 <= rd <= 31, written to x0 must be filtered out by caller
    //
    // In case of XLEN < 8 * sizeof(value)
    // - the callee must treat upper bits as unspecfied
    // - the caller is recommended to sign-extend the value, following RISC-V convention
    void (*log_write_xreg)(void* data, uint8_t rd, uint32_t value);

    // 0 <= fd <= 31
    //
    // In case of XLEN < 8 * sizeof(value)
    // - the callee must treat upper bits as unspecfied
    // - the caller is recommended to sign-extend the value, following RISC-V convention
    void (*log_write_freg)(void* data, uint8_t fd, uint32_t value);

    // 0 <= vd <= 31
    //
    // value should contain the whole register, byte_len should equal to vlenb csr
    void (*log_write_vreg)(void* data, uint8_t vd, const uint8_t* value, size_t byte_len);

    void (*log_write_csr)(void* data, const char* name, uint32_t value);

    // print some diagnotic-only meesage
    void (*debug_print)(void* data, const char* message);
};

typedef const struct pokedex_vtable* (*get_pokedex_vtable_t)();

// Example:
//
// static struct pokedex_vtable asl_pokedex_vtable = {
//     .abi_version = POKEDEX_ABI_VERSION,
//     .model_isa = "...",
//     .create = ...,
//     .destroy = ...,
//
//     ...
// };
// 
// // This is the only required export symbol
// const struct pokedex_vtable* ASL_MODEL_get_pokedex_vtable() {
//   return &asl_pokedex_vtable;
// }

#ifdef __cplusplus
}
#endif

#endif // include guard
