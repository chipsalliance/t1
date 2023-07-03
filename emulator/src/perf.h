#pragma once

#include <glog/logging.h>
#include <fmt/core.h>

#include "encoding.h"
#include "svdpi.h"
#include "vbridge_impl.h"
#include "exceptions.h"

struct VRFPerf {
  int64_t total_records = 0;
  int64_t vrf_write_records = 0;

  void print_summary(std::ostream &os) const {
    os << fmt::format("vrf_write_records: {}/{} ({:.3f}%)\n",
                      vrf_write_records, total_records, (double) vrf_write_records / (double) total_records * 100.);
  }

  void step(int lane_idx, bool is_write) {
    // TODO: consider laneIdx
    total_records++;
    vrf_write_records += (int64_t) is_write;
  }
};

struct ALUPerf {
  int64_t total_records = 0;
  int64_t adder_occupied_records = 0;
  int64_t shifter_occupied_records = 0;
  int64_t multiplier_occupied_records = 0;
  int64_t divider_occupied_records = 0;

  void print_summary(std::ostream &os) const {
    os << fmt::format(
        "adder_occupied: {}/{} ({:.3f}%)\n"
        "shifter_occupied: {}/{} ({:.3f}%)\n"
        "multiplier_occupied: {}/{} ({:.3f}%)\n"
        "divider_occupied: {}/{} ({:.3f}%)\n",
        adder_occupied_records, total_records, (double) adder_occupied_records / (double) total_records * 100.,
        shifter_occupied_records, total_records, (double) shifter_occupied_records / (double) total_records * 100.,
        multiplier_occupied_records, total_records, (double) multiplier_occupied_records / (double) total_records * 100.,
        divider_occupied_records, total_records, (double) divider_occupied_records / (double) total_records * 100.
    );
  }

  void step(int lane_idx,
            bool is_adder_occupied, bool is_shifter_occupied,
            bool is_multiplier_occupied, bool is_divider_occupied) {
    total_records ++;
    adder_occupied_records += (int64_t) is_adder_occupied;
    shifter_occupied_records += (int64_t) is_shifter_occupied;
    multiplier_occupied_records += (int64_t) is_multiplier_occupied;
    divider_occupied_records += (int64_t) is_divider_occupied;
  }
};

struct LSUPerf {
  int pending_req_num;
  int64_t total_records;
  int64_t total_pending_reqs;
  int64_t total_occupied_lsu;

  bool last_a_valid, last_d_ready, last_d_valid, last_a_ready;

  void print_summary(std::ostream &os, int bank_idx) const {
    os << fmt::format(
        "bank_{}_avg_pending_req: {}/{} ({:.3f})\n"
        "bank_{}_occupied: {}/{} ({:.3f}%)\n",
        bank_idx, total_pending_reqs, total_records, (double) total_pending_reqs / (double) total_records,
        bank_idx, total_occupied_lsu, total_records, (double) total_occupied_lsu / (double) total_records * 100.
    );
  }

  void peek_tl(bool a_valid, bool d_ready) {
    last_a_valid = a_valid;
    last_d_ready = d_ready;
  }

  void poke_tl(bool d_valid, bool a_ready) {
    last_d_valid = d_valid;
    last_a_ready = a_ready;
  }

  void step() {
    if (last_a_valid && last_a_ready) {
      pending_req_num ++;
    }
    if (last_d_valid && last_d_ready) {
      pending_req_num --;
    }
    total_records ++;
    total_pending_reqs += pending_req_num;
    total_occupied_lsu += (int64_t) pending_req_num != 0;
  }
};

struct ChainingPerf {
  int64_t total_records = 0;
  int64_t total_occupied = 0;

  void print_summary(std::ostream &os) const {
    os << fmt::format("chaining_size: {}/{} ({:.3f}%)\n",
                      total_occupied, total_records, (double) total_occupied / (double) total_records * 100.);
  }

  void step(int lane_idx, const svBitVecVal *slot_occupied) {
    total_records++;
    total_occupied += (int64_t) __builtin_popcount(*slot_occupied);
  }
};
