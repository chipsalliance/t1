// This file includes DPI call implementatitons

#include "svdpi.h"

#include "dpi.h"

extern "C" {

void *dpi_call_target;

extern svLogic DumpWave(const char *file);

extern void axi_read_instructionFetchAXI(long long channel_id, long long ar_id,
                                         long long ar_addr, long long ar_len,
                                         long long ar_size, long long ar_burst,
                                         long long ar_lock, long long ar_cache,
                                         long long ar_prot, long long ar_qos,
                                         long long ar_region,
                                         svBitVecVal *payload) {
  axi_read_instructionFetchAXI_rs(dpi_call_target, channel_id, ar_id, ar_addr,
                                  ar_len, ar_size, ar_burst, ar_lock, ar_cache,
                                  ar_prot, ar_qos, ar_region, payload);
};

extern void axi_read_loadStoreAXI(void *dpi_call_target, long long channel_id,
                                  long long ar_id, long long ar_addr,
                                  long long ar_len, long long ar_size,
                                  long long ar_burst, long long ar_lock,
                                  long long ar_cache, long long ar_prot,
                                  long long ar_qos, long long ar_region,
                                  svBitVecVal *payload) {
  axi_read_loadStoreAXI(dpi_call_target, channel_id, ar_id, ar_addr, ar_len,
                        ar_size, ar_burst, ar_lock, ar_cache, ar_prot, ar_qos,
                        ar_region, payload);
};

extern void axi_write_loadStoreAXI(long long channel_id, long long aw_id,
                                   long long aw_addr, long long aw_len,
                                   long long aw_size, long long aw_burst,
                                   long long aw_lock, long long aw_cache,
                                   long long aw_prot, long long aw_qos,
                                   long long aw_region,
                                   const svBitVecVal *payload) {
  axi_write_loadStoreAXI_rs(dpi_call_target, channel_id, aw_id, aw_addr, aw_len,
                            aw_size, aw_burst, aw_lock, aw_cache, aw_prot,
                            aw_qos, aw_region, payload);
};

extern void cosim_init(svBit *call_init) {
    dpi_call_target = cosim_init_rs(call_init);
};

extern void cosim_watchdog(char *reason) {
    cosim_watchdog_rs(dpi_call_target, reason);
};

} // extern "C"
