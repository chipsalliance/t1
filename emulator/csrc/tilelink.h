#pragma once

namespace TlOpcode {
  constexpr int Get = 4, AccessAckData = 1, PutFullData = 0, PutPartialData = 1, AccessAck = 0;
}

struct VTlInterface {
  uint32_t channel_id;
  svBitVecVal a_bits_opcode;
  svBitVecVal a_bits_param;
  svBitVecVal a_bits_size;
  svBitVecVal a_bits_source;
  svBitVecVal a_bits_address;
  const svBitVecVal *a_bits_mask;
  const svBitVecVal *a_bits_data;
  svBit a_corrupt;
  svBit a_valid;
  svBit d_ready;
};

struct VTlInterfacePoke {
  uint32_t channel_id;
  svBitVecVal *d_bits_opcode;
  svBitVecVal *d_bits_param;
  svBitVecVal *d_bits_size;
  svBitVecVal *d_bits_source;
  svBitVecVal *d_bits_sink;
  svBit *d_bits_denied;
  svBitVecVal *d_bits_data;
  svBit *d_corrupt;
  svBit *d_valid;
  svBit *a_ready;
  svBit d_ready;
};
