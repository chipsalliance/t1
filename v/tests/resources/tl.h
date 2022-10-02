#pragma once

struct csrInterface {
  uint32_t vl;
  uint32_t vStart;
  uint32_t vlmul;
  uint32_t vSew;
  uint32_t vxrm;
  bool vta;
  bool vma;
  bool ignoreException;
};

struct tlA {
  uint32_t bits_data;
  uint32_t bits_address;

  bool valid: 1;
  bool bits_corrupt: 1;
  uint32_t bits_opcode: 3;
  uint32_t bits_size: 2;
  uint32_t bits_source: 10;
  uint32_t bits_mask: 4;
};

struct tlD {
  uint32_t bits_data;

  bool valid: 1;
  bool bits_denied: 1;
  bool bits_corrupt: 1;
  uint32_t bits_opcode: 3;
  uint32_t bits_size: 2;
  uint32_t bits_source: 10;
  uint32_t bits_sink: 10;
};

namespace TLOpCode {
constexpr uint8_t PutFullData = 0;
constexpr uint8_t PutPartialData = 1;
constexpr uint8_t Get = 4;
constexpr uint8_t AccessAck = 0;
constexpr uint8_t AccessAckData = 1;
};

struct v_resp {
  uint32_t rd_data;
  bool valid;
  bool tl_a_ready[2];
  tlD tl_d[2];
};

struct v_req {
  uint32_t insn_bits;
  uint32_t rs1_data;
  uint32_t rs2_data;
  bool sb_clear;
  bool valid;
  tlA tl_a[2];
  bool tl_d_ready[2];
};

