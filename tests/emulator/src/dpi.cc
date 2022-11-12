#include "VTestBench__Dpi.h"

#include "svdpi.h"
#include <fmt/core.h>
#include <glog/logging.h>
#include "SimulationContext.h"

void dpiTimeoutCheck() {
  SimulationContext::getInstance().timeoutCheck();
}

void dpiInitCosim() {
  SimulationContext::getInstance().initCosim();
}

void dpiInstructionFire(svLogic ready, const svBitVecVal* index) {
  if (ready) SimulationContext::getInstance().instructionFire(index);
}

void dpiPeekResponse(svBit valid, const svBitVecVal* bits) {
  if (valid) SimulationContext::getInstance().peekResponse(bits);
}

void dpiPokeCSR(svBitVecVal* vl, svBitVecVal* vStart, svBitVecVal* vlmul, svBitVecVal* vSew, svBitVecVal* vxrm, svBit* vta, svBit* vma, svBit* ignoreException) {
  SimulationContext::getInstance().pokeCSR(vl, vStart, vlmul, vSew, vxrm, vta, vma, ignoreException);
}

void dpiPokeInstruction(svBitVecVal* request_inst, svBitVecVal* request_src1Data, svBitVecVal* request_src2Data, svBit* valid){
  SimulationContext::getInstance().pokeInstruction(request_inst, request_src1Data, request_src2Data, valid);
}
