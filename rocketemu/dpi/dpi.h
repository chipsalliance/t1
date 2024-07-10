// This file includes DPI calls to be implemented in Rust

#pragma once

#include "svdpi.h"

#ifdef __cplusplus
extern "C" {
#endif

extern void *dpi_call_target;

// Parameters came from AXIAgent.scala
extern void axi_read_instructionFetchAXI_rs(
    void *dpi_call_target, long long channel_id, long long ar_id,
    long long ar_addr, long long ar_len, long long ar_size, long long ar_burst,
    long long ar_lock, long long ar_cache, long long ar_prot, long long ar_qos,
    long long ar_region, svBitVecVal *payload);

extern void axi_read_loadStoreAXI_rs(void *dpi_call_target,
                                     long long channel_id, long long ar_id,
                                     long long ar_addr, long long ar_len,
                                     long long ar_size, long long ar_burst,
                                     long long ar_lock, long long ar_cache,
                                     long long ar_prot, long long ar_qos,
                                     long long ar_region, svBitVecVal *payload);

extern void axi_write_loadStoreAXI_rs(
    void *dpi_call_target, long long channel_id, long long aw_id,
    long long aw_addr, long long aw_len, long long aw_size, long long aw_burst,
    long long aw_lock, long long aw_cache, long long aw_prot, long long aw_qos,
    long long aw_region, const svBitVecVal *payload);

extern void* cosim_init_rs(svBit *call_init);

extern void cosim_watchdog_rs(void *dpi_call_target, char *reason);

#ifdef __cplusplus
}
#endif
