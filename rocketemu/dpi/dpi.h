// This file includes DPI calls to be implemented in Rust

#pragma once

#include "svdpi.h"

#ifdef __cplusplus
extern "C" {
#endif

extern void *dpi_call_target;

/// evaluate after AW and W is finished at corresponding channel_id.
extern void
axi_write_loadStoreAXI_rs(void *dpi_call_target, long long channel_id,
                          long long awid, long long awaddr, long long awlen,
                          long long awsize, long long awburst, long long awlock,
                          long long awcache, long long awprot, long long awqos,
                          long long awregion,
                          /// struct packed {bit [255:0][DLEN:0] data; bit
                          /// [255:0][DLEN/8:0] strb; } payload
                          const svBitVecVal *payload);

/// evaluate at AR fire at corresponding channel_id.
extern void axi_read_loadStoreAXI_rs(
    void *dpi_call_target, long long channel_id, long long arid,
    long long araddr, long long arlen, long long arsize, long long arburst,
    long long arlock, long long arcache, long long arprot, long long arqos,
    long long arregion,
    /// struct packed {bit [255:0][DLEN:0] data; byte beats; } payload
    svBitVecVal *payload);

/// evaluate at AR fire at corresponding channel_id.
extern void axi_read_instructionFetchAXI_rs(
    void *dpi_call_target, long long channel_id, long long arid,
    long long araddr, long long arlen, long long arsize, long long arburst,
    long long arlock, long long arcache, long long arprot, long long arqos,
    long long arregion,
    /// struct packed {bit [255:0][31:0] data; byte beats; } payload
    svBitVecVal *payload);

/// evaluate after reset, and will only be called once returning *call_init =
/// true. returns dpi call target
extern void *cosim_init_rs();

/// evaluate at every 1024 cycles, return reason = 0 to continue simulation,
/// other value is used as error code.
extern void cosim_watchdog_rs(void *dpi_call_target, char *reason);

#ifdef __cplusplus
}
#endif
