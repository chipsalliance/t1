#include <VV.h>

namespace TlOpcode {
  constexpr int
  Get = 4,
  AccessAckData = 1,
  PutFullData = 0,
  PutPartialData = 1,
  AccessAck = 4;
}

// the following macro helps us to access tl interface by dynamic index
#define TL_INTERFACE(type, name) \
[[nodiscard]] inline type &get_tl_##name(VV &top, int i) {      \
  switch (i) {                   \
  case 0: return top.tlPort_0_##name;                     \
  case 1: return top.tlPort_1_##name;                     \
  default: assert(false && "unknown tl port index");                                 \
  }\
}

TL_INTERFACE(CData, d_ready);
TL_INTERFACE(CData, d_valid);
TL_INTERFACE(CData, d_bits_opcode);
TL_INTERFACE(CData, d_bits_param);
TL_INTERFACE(CData, d_bits_size);
TL_INTERFACE(CData, d_bits_denied);
TL_INTERFACE(CData, d_bits_corrupt);
TL_INTERFACE(CData, a_ready);
TL_INTERFACE(CData, a_valid);
TL_INTERFACE(CData, a_bits_opcode);
TL_INTERFACE(CData, a_bits_param);
TL_INTERFACE(CData, a_bits_size);
TL_INTERFACE(CData, a_bits_mask);
TL_INTERFACE(CData, a_bits_corrupt);
TL_INTERFACE(SData, d_bits_source);
TL_INTERFACE(SData, d_bits_sink);
TL_INTERFACE(SData, a_bits_source);
TL_INTERFACE(IData, d_bits_data);
TL_INTERFACE(IData, a_bits_address);
TL_INTERFACE(IData, a_bits_data);

#undef TL_INTERFACE
