#pragma once

#include <svdpi.h>
#include <cstdint>
#include <set>
#include <cstring>
#include "auto_sig.hpp"

struct VTlInterfacePeek {
  const svBitVecVal *a_opcode;
  const svBitVecVal *a_param;
  const svBitVecVal *a_size;
  const svBitVecVal *a_source;
  const svBitVecVal *a_address;
  const svBitVecVal *a_mask;
  const svBitVecVal *a_data;
  svBit a_corrupt;
  svBit a_valid;
  svBit d_ready;
  VTlInterfacePeek(const svBitVecVal *a_opcode, const svBitVecVal *a_param,
                   const svBitVecVal *a_size, const svBitVecVal *a_source,
                   const svBitVecVal *a_address, const svBitVecVal *a_mask,
                   const svBitVecVal *a_data, svBit a_corrupt, svBit a_valid,
                   svBit d_ready)
      : a_opcode(a_opcode), a_param(a_param), a_size(a_size), a_source(a_source),
        a_address(a_address), a_mask(a_mask), a_data(a_data), a_corrupt(a_corrupt),
        a_valid(a_valid), d_ready(d_ready) {}
};

struct VTlInterfacePoke {
  svBitVecVal *d_opcode;
  svBitVecVal *d_param;
  svBitVecVal *d_size;
  svBitVecVal *d_source;
  svBit *d_sink;
  svBit *d_denied;
  svBitVecVal *d_data;
  svBit *d_corrupt;
  svBit *d_valid;
  svBit *a_ready;
  VTlInterfacePoke(svBitVecVal *d_opcode, svBitVecVal *d_param,
                   svBitVecVal *d_size, svBitVecVal *d_source, svBit *d_sink,
                   svBit *d_denied, svBitVecVal *d_data, svBit *d_corrupt,
                   svBit *d_valid, svBit *a_ready)
      : d_opcode(d_opcode), d_param(d_param), d_size(d_size), d_source(d_source),
        d_sink(d_sink), d_denied(d_denied), d_data(d_data), d_corrupt(d_corrupt),
        d_valid(d_valid), a_ready(a_ready) {}
};


template <unsigned int A_WIDTH, unsigned int W_WIDTH, unsigned int O_WIDTH,
          unsigned int Z_WIDTH>
struct tilelink;

template <unsigned int A_WIDTH, unsigned int W_WIDTH, unsigned int O_WIDTH,
          unsigned int Z_WIDTH>
struct tilelink_ptr {
    // a
    AUTO_IN (*a_bits_opcode , 2             , 0)    = NULL;
    AUTO_IN (*a_bits_param  , 2             , 0)    = NULL;
    AUTO_IN (*a_bits_size   , Z_WIDTH-1     , 0)    = NULL;
    AUTO_IN (*a_bits_source , O_WIDTH-1     , 0)    = NULL;
    AUTO_IN (*a_bits_address, A_WIDTH-1     , 0)    = NULL;
    AUTO_IN (*a_bits_mask   , W_WIDTH-1     , 0)    = NULL;
    AUTO_IN (*a_bits_data   , W_WIDTH*8-1   , 0)    = NULL;
    AUTO_IN (*a_bits_corrupt, 0             , 0)    = NULL;
    AUTO_IN (*a_valid       , 0             , 0)    = NULL;
    AUTO_OUT(*a_ready       , 0             , 0)    = NULL;
    // d
    AUTO_OUT(*d_bits_opcode , 2             , 0)    = NULL;
    AUTO_OUT(*d_bits_param  , 2             , 0)    = NULL;
    AUTO_OUT(*d_bits_size   , Z_WIDTH-1     , 0)    = NULL;
    AUTO_OUT(*d_bits_source , O_WIDTH-1     , 0)    = NULL;
    // no sink here for TL-UH
    AUTO_OUT(*d_bits_denied , 0             , 0)    = NULL;
    AUTO_OUT(*d_bits_data   , W_WIDTH*8-1   , 0)    = NULL;
    AUTO_OUT(*d_bits_corrupt, 0             , 0)    = NULL;
    AUTO_OUT(*d_valid       , 0             , 0)    = NULL;
    AUTO_IN (*d_ready       , 0             , 0)    = NULL;
    bool check() {
        std::set <void*> s;
        s.insert((void*)a_bits_opcode);
        s.insert((void*)a_bits_param);
        s.insert((void*)a_bits_size);
        s.insert((void*)a_bits_source);
        s.insert((void*)a_bits_address);
        s.insert((void*)a_bits_mask);
        s.insert((void*)a_bits_data);
        s.insert((void*)a_bits_corrupt);
        s.insert((void*)a_valid);
        s.insert((void*)a_ready);
        s.insert((void*)d_bits_opcode);
        s.insert((void*)d_bits_param);
        s.insert((void*)d_bits_size);
        s.insert((void*)d_bits_source);
        s.insert((void*)d_bits_denied);
        s.insert((void*)d_bits_data);
        s.insert((void*)d_bits_corrupt);
        s.insert((void*)d_valid);
        s.insert((void*)d_ready);
        return s.size() == 19;
    }
};

template <unsigned int A_WIDTH, unsigned int W_WIDTH, unsigned int O_WIDTH,
          unsigned int Z_WIDTH>
struct tilelink_ref {
    // a
    AUTO_IN (&a_bits_opcode , 2             , 0);
    AUTO_IN (&a_bits_param  , 2             , 0);
    AUTO_IN (&a_bits_size   , Z_WIDTH-1     , 0);
    AUTO_IN (&a_bits_source , O_WIDTH-1     , 0);
    AUTO_IN (&a_bits_address, A_WIDTH-1     , 0);
    AUTO_IN (&a_bits_mask   , W_WIDTH-1     , 0);
    AUTO_IN (&a_bits_data   , W_WIDTH*8-1   , 0);
    AUTO_IN (&a_bits_corrupt, 0             , 0);
    AUTO_IN (&a_valid       , 0             , 0);
    AUTO_OUT(&a_ready       , 0             , 0);
    // d
    AUTO_OUT(&d_bits_opcode , 2             , 0);
    AUTO_OUT(&d_bits_param  , 2             , 0);
    AUTO_OUT(&d_bits_size   , Z_WIDTH-1     , 0);
    AUTO_OUT(&d_bits_source , O_WIDTH-1     , 0);
    // no sink here for TL-UH
    AUTO_OUT(&d_bits_denied , 0             , 0);
    AUTO_OUT(&d_bits_data   , W_WIDTH*8-1   , 0);
    AUTO_OUT(&d_bits_corrupt, 0             , 0);
    AUTO_OUT(&d_valid       , 0             , 0);
    AUTO_IN (&d_ready       , 0             , 0);

    tilelink_ref(tilelink_ptr <A_WIDTH, W_WIDTH, O_WIDTH, Z_WIDTH> &p):
        a_bits_opcode (*(p.a_bits_opcode)),
        a_bits_param  (*(p.a_bits_param)),
        a_bits_size   (*(p.a_bits_size)),
        a_bits_source (*(p.a_bits_source)),
        a_bits_address(*(p.a_bits_address)),
        a_bits_mask   (*(p.a_bits_mask)),
        a_bits_data   (*(p.a_bits_data)),
        a_bits_corrupt(*(p.a_bits_corrupt)),
        a_valid       (*(p.a_valid)),
        a_ready       (*(p.a_ready)),
        d_bits_opcode (*(p.d_bits_opcode)),
        d_bits_param  (*(p.d_bits_param)),
        d_bits_size   (*(p.d_bits_size)),
        d_bits_source (*(p.d_bits_source)),
        d_bits_denied (*(p.d_bits_denied)),
        d_bits_data   (*(p.d_bits_data)),
        d_bits_corrupt(*(p.d_bits_corrupt)),
        d_valid       (*(p.d_valid)),
        d_ready       (*(p.d_ready)) {
    }

    tilelink_ref(tilelink <A_WIDTH, W_WIDTH, O_WIDTH, Z_WIDTH> &p);
};

template <unsigned int A_WIDTH, unsigned int W_WIDTH, unsigned int O_WIDTH,
          unsigned int Z_WIDTH>
struct tilelink {
    // a
    AUTO_IN (a_bits_opcode , 2              , 0);
    AUTO_IN (a_bits_param  , 2              , 0);
    AUTO_IN (a_bits_size   , Z_WIDTH-1      , 0);
    AUTO_IN (a_bits_source , O_WIDTH-1      , 0);
    AUTO_IN (a_bits_address, A_WIDTH-1      , 0);
    AUTO_IN (a_bits_mask   , W_WIDTH-1      , 0);
    AUTO_IN (a_bits_data   , W_WIDTH*8-1    , 0);
    AUTO_IN (a_bits_corrupt, 0              , 0);
    AUTO_IN (a_valid       , 0              , 0);
    AUTO_OUT(a_ready       , 0              , 0);
    // d
    AUTO_OUT(d_bits_opcode , 2              , 0);
    AUTO_OUT(d_bits_param  , 2              , 0);
    AUTO_OUT(d_bits_size   , Z_WIDTH-1      , 0);
    AUTO_OUT(d_bits_source , O_WIDTH-1      , 0);
    // no sink here for TL-UH
    AUTO_OUT(d_bits_denied , 0              , 0);
    AUTO_OUT(d_bits_data   , W_WIDTH*8-1    , 0);
    AUTO_OUT(d_bits_corrupt, 0              , 0);
    AUTO_OUT(d_valid       , 0              , 0);
    AUTO_IN (d_ready       , 0              , 0);
    
    tilelink() {
        // reset all pointer to zero
        memset(this, 0, sizeof(*this));
    }

    void update_input(tilelink_ref <A_WIDTH, W_WIDTH, O_WIDTH, Z_WIDTH> &p) {
        a_bits_opcode  = p.a_bits_opcode;
        a_bits_param   = p.a_bits_param;
        a_bits_size    = p.a_bits_size;
        a_bits_source  = p.a_bits_source;
        a_bits_address = p.a_bits_address;
        a_bits_mask    = p.a_bits_mask;
        a_bits_data    = p.a_bits_data;
        a_bits_corrupt = p.a_bits_corrupt;
        a_valid        = p.a_valid;
        d_ready        = p.d_ready;
    }

    void update_output(tilelink_ref <A_WIDTH, W_WIDTH, O_WIDTH, Z_WIDTH> &p) {
        p.a_ready        = a_ready;
        p.d_bits_opcode  = d_bits_opcode;
        p.d_bits_param   = d_bits_param;
        p.d_bits_size    = d_bits_size;
        p.d_bits_source  = d_bits_source;
        p.d_bits_denied  = d_bits_denied;
        p.d_bits_data    = d_bits_data;
        p.d_bits_corrupt = d_bits_corrupt;
        p.d_valid        = d_valid;
    }

    void update_input_dpi(VTlInterfacePeek &peek) {
        a_bits_opcode  = *reinterpret_cast<const AUTO_TYPE(2          , 0)*>(peek.a_opcode);
        a_bits_param   = *reinterpret_cast<const AUTO_TYPE(2          , 0)*>(peek.a_param);
        a_bits_size    = *reinterpret_cast<const AUTO_TYPE(Z_WIDTH-1  , 0)*>(peek.a_size);
        a_bits_source  = *reinterpret_cast<const AUTO_TYPE(O_WIDTH-1  , 0)*>(peek.a_source);
        a_bits_address = *reinterpret_cast<const AUTO_TYPE(A_WIDTH-1  , 0)*>(peek.a_address);
        a_bits_mask    = *reinterpret_cast<const AUTO_TYPE(W_WIDTH-1  , 0)*>(peek.a_mask);
        a_bits_data    = *reinterpret_cast<const AUTO_TYPE(W_WIDTH*8-1, 0)*>(peek.a_data);
        a_bits_corrupt = peek.a_corrupt;
        a_valid        = peek.a_valid;
        d_ready        = peek.d_ready;
    }

    void update_output_dpi(VTlInterfacePoke &poke) {
        *reinterpret_cast<AUTO_TYPE(2          , 0)*>(poke.d_opcode)  = d_bits_opcode;
        *reinterpret_cast<AUTO_TYPE(2          , 0)*>(poke.d_param)   = d_bits_param;
        *reinterpret_cast<AUTO_TYPE(Z_WIDTH-1  , 0)*>(poke.d_size)    = d_bits_size;
        *reinterpret_cast<AUTO_TYPE(O_WIDTH-1  , 0)*>(poke.d_source)  = d_bits_source;
        *reinterpret_cast<AUTO_TYPE(0          , 0)*>(poke.d_sink)    = 0;
        *reinterpret_cast<AUTO_TYPE(0          , 0)*>(poke.d_denied)  = d_bits_denied;
        *reinterpret_cast<AUTO_TYPE(W_WIDTH*8-1, 0)*>(poke.d_data)    = d_bits_data;
        *reinterpret_cast<AUTO_TYPE(0          , 0)*>(poke.d_corrupt) = d_bits_corrupt;
        *reinterpret_cast<AUTO_TYPE(0          , 0)*>(poke.d_valid)   = d_valid;
        *reinterpret_cast<AUTO_TYPE(0          , 0)*>(poke.a_ready)   = a_ready;
    }
};

template <unsigned int A_WIDTH, unsigned int W_WIDTH, unsigned int O_WIDTH,
          unsigned int Z_WIDTH>
tilelink_ref <A_WIDTH, W_WIDTH, O_WIDTH, Z_WIDTH>::
tilelink_ref(tilelink <A_WIDTH, W_WIDTH, O_WIDTH, Z_WIDTH> &p):
    a_bits_opcode (p.a_bits_opcode),
    a_bits_param  (p.a_bits_param),
    a_bits_size   (p.a_bits_size),
    a_bits_source (p.a_bits_source),
    a_bits_address(p.a_bits_address),
    a_bits_mask   (p.a_bits_mask),
    a_bits_data   (p.a_bits_data),
    a_bits_corrupt(p.a_bits_corrupt),
    a_valid       (p.a_valid),
    a_ready       (p.a_ready),
    d_bits_opcode (p.d_bits_opcode),
    d_bits_param  (p.d_bits_param),
    d_bits_size   (p.d_bits_size),
    d_bits_source (p.d_bits_source),
    d_bits_denied (p.d_bits_denied),
    d_bits_data   (p.d_bits_data),
    d_bits_corrupt(p.d_bits_corrupt),
    d_valid       (p.d_valid),
    d_ready       (p.d_ready) {
}

enum opcode_a {
    TL_A_PutFullData = 0x0,
    TL_A_PutPartialData = 0x1,
    TL_A_ArithmeticData = 0x2,
    TL_A_LogicalData = 0x3,
    TL_A_Get = 0x4,
    TL_A_Intent = 0x5
};

enum opcode_d {
    TL_D_AccessAck = 0x0,
    TL_D_AccessAckData = 0x1,
    TL_D_HintAck = 0x2
};

enum opcode_ArithmeticData {
    TL_PARAM_MIN    = 0x0,
    TL_PARAM_MAX    = 0x1,
    TL_PARAM_MINU   = 0x2,
    TL_PARAM_MAXU   = 0x3,
    TL_PARAM_ADD    = 0x4
};

enum opcode_LogicalData {
    TL_PARAM_XOR    = 0x0,
    TL_PARAM_OR     = 0x1,
    TL_PARAM_AND    = 0x2,
    TL_PARAM_SWAP   = 0x3
};

/*
    Note: packet data and mask alignment is not same as AXI, we have no
    any padding here.
 */

struct a_packet {
    opcode_a opcode;
    uint8_t param;
    uint64_t size;
    uint64_t source;
    uint64_t address;
    std::vector <char> data;
    std::vector <bool> mask;
    bool corrupt;
};

struct d_packet {
    opcode_d opcode;
    uint8_t param;
    uint64_t size;
    uint64_t source;
    uint64_t address; // for data padding calculation
    std::vector <char> data;
    bool corrupt;
    bool denied;
};
