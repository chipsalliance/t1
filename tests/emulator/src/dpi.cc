#ifdef COSIM_VERILATOR

#include "VTestBench__Dpi.h"

#endif

#include "svdpi.h"
#include "SimulationContext.h"

void dpiInitCosim() {
  SimulationContext::getInstance().initCosim();
}

void dpiInstructionFire(svLogic ready, const svBitVecVal *index) {
  if (ready) SimulationContext::getInstance().instructionFire(index);
}

void dpiPeekResponse(svBit valid, const svBitVecVal *bits) {
  if (valid) SimulationContext::getInstance().peekResponse(bits);
}

void
dpiPokeCSR(svBitVecVal *vl, svBitVecVal *vStart, svBitVecVal *vlmul, svBitVecVal *vSew, svBitVecVal *vxrm, svBit *vta,
           svBit *vma, svBit *ignoreException) {
  SimulationContext::getInstance().pokeCSR(vl, vStart, vlmul, vSew, vxrm, vta, vma, ignoreException);
}

void dpiPokeInstruction(svBitVecVal *request_inst, svBitVecVal *request_src1Data, svBitVecVal *request_src2Data,
                        svBit *valid) {
  SimulationContext::getInstance().pokeInstruction(request_inst, request_src1Data, request_src2Data, valid);
}

void dpiPeekTL(int channel_id, const svBitVecVal *a_opcode, const svBitVecVal *a_param, const svBitVecVal *a_size,
               const svBitVecVal *a_source, const svBitVecVal *a_address, const svBitVecVal *a_mask,
               const svBitVecVal *a_data, svBit a_corrupt, svBit a_valid, svBit d_ready) {
  SimulationContext::getInstance().dpiPeekTL(channel_id, a_opcode, a_param, a_size, a_source, a_address, a_mask, a_data,
                                             a_corrupt, a_valid, d_ready);
}

void dpiPokeTL(int channel_id, svBitVecVal *d_opcode, svBitVecVal *d_param, svBitVecVal *d_size, svBitVecVal *d_source,
               svBitVecVal *d_sink, svBitVecVal *d_denied, svBitVecVal *d_data, svBit *d_corrupt, svBit *d_valid,
               svBit *a_ready) {
  SimulationContext::getInstance().dpiPokeTL(channel_id, d_opcode, d_param, d_size, d_source, d_sink, d_denied, d_data,
                                             d_corrupt, d_valid, a_ready);
}

void dpiTimeoutCheck() {
  SimulationContext::getInstance().timeoutCheck();
}
