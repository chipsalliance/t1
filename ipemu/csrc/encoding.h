#ifdef IPEMU
#pragma once
#include <svdpi.h>
#include "tilelink.h"

// Write this CSR to end simulation.
constexpr uint32_t CSR_MSIMEND = 0x7cc;

struct VCsrInterfacePoke {
  svBitVecVal *vl;
  svBitVecVal *vStart;
  svBitVecVal *vlmul;
  svBitVecVal *vSew;
  svBitVecVal *vxrm;
  svBit *vta;
  svBit *vma;
  svBit *ignoreException;
};

struct VInstrInterfacePoke {
  svBitVecVal *inst;
  svBitVecVal *src1Data;
  svBitVecVal *src2Data;
  svBit *valid;
};

struct VRespInterface {
  svBit valid;
  svBitVecVal data;
  svBit vxsat;
};

struct VInstrFire {
  svBit ready;
  svBitVecVal index;
};

struct VLsuWriteQueuePeek {
  uint32_t mshr_index;
  svBit write_valid;
  svBitVecVal request_data_vd;
  svBitVecVal request_data_offset;
  svBitVecVal request_data_mask;
  svBitVecVal request_data_data;
  svBitVecVal request_data_instIndex;
  svBitVecVal request_targetLane;
};

struct VrfWritePeek {
  uint32_t lane_index;
  svBit valid;
  svBitVecVal request_vd;
  svBitVecVal request_offset;
  svBitVecVal request_mask;
  svBitVecVal request_data;
  svBitVecVal request_instIndex;
};

struct VLsuReqEnqPeek {
  svBitVecVal enq;
};
#endif