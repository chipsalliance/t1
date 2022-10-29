#pragma once

namespace consts {

// simulation arch config
constexpr int vlen_in_bits = 1024;
constexpr int elen = 32;

// const as default value
constexpr int lsuIdxDefault = 255;

// rtl parameters
constexpr int numTL = 2;
constexpr int numMSHR = 3;
constexpr int numLanes = 8;

}
