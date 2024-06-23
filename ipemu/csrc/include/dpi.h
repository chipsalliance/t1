#include "svdpi.h"

// axi_read_${name} and axi_write_${name} should be de-duplicated. see https://github.com/llvm/circt/issues/7227

/// evaluate after AW and W is finished at corresponding channel_id.
extern void axi_write_highBandwidthPort(
    long long channel_id,
    long long awid,
    long long awaddr,
    long long awlen,
    long long awsize,
    long long awburst,
    long long awlock,
    long long awcache,
    long long awprot,
    long long awqos,
    long long awregion,
    /// struct packed {bit [255:0][DLEN:0] data; bit [255:0][DLEN/8:0] strb; } payload
    const svBitVecVal* payload
);

/// evaluate at AR fire at corresponding channel_id.
extern void axi_read_highBandwidthPort(
    long long channel_id,
    long long arid,
    long long araddr,
    long long arlen,
    long long arsize,
    long long arburst,
    long long arlock,
    long long arcache,
    long long arprot,
    long long arqos,
    long long arregion,
    /// struct packed {bit [255:0][DLEN:0] data; byte beats; } payload
    svBitVecVal* payload
);

/// evaluate at AR fire at corresponding channel_id.
extern void axi_read_indexedAccessPort(
    long long channel_id,
    long long arid,
    long long araddr,
    long long arlen,
    long long arsize,
    long long arburst,
    long long arlock,
    long long arcache,
    long long arprot,
    long long arqos,
    long long arregion,
    /// struct packed {bit [255:0][31:0] data; byte beats; } payload
    svBitVecVal* payload
);


/// evaluate after AW and W is finished at corresponding channel_id.
extern void axi_write_indexedAccessPort(
    long long channel_id,
    long long awid,
    long long awaddr,
    long long awlen,
    long long awsize,
    long long awburst,
    long long awlock,
    long long awcache,
    long long awprot,
    long long awqos,
    long long awregion,
    /// struct packed {bit [255:0][32:0] data; bit [255:0][4:0] strb; } payload
    const svBitVecVal* payload
);

/// evaluate after reset, and will only be called once returning *call_init = true.
extern void cosim_init(
    svBit* call_init
);

/// evaluate at every 1024 cycles, return reason = 0 to continue simulation, other value is used as error code.
extern void cosim_watchdog(
    char* reason
);

/// evaluate at instruction queue is not empty
/// arg issue will be type cast from a struct to svBitVecVal*(uint32_t*)
extern void issue_vector_instruction(
    /// struct issue_data {
    ///   uint32_t instruction;
    ///   uint32_t src1_data;
    ///   uint32_t src2_data;
    ///   uint32_t vtype;
    ///   uint32_t vl;
    ///   uint32_t vstart;
    ///   uint32_t vcsr;
    /// }
    svBitVecVal* issue
);
