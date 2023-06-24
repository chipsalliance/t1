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
  int64_t vrf_read_records = 0;

  void print_summary(std::ostream &os) const {
    double vrf_write_rate = (double) vrf_write_records / (double) total_records;
    double vrf_read_rate = (double) vrf_read_records / (double) total_records;
    os << fmt::format(
      "vrf_write_records: {}/{} ({:.3f}%) ({:.3f} ops per cycle)\n"
      "vrf_read_records: {}/{} ({:.3f} ops per cycle)\n",
      vrf_write_records, total_records, vrf_write_rate * 100., vrf_write_rate * consts::numLanes,
      vrf_read_records, total_records, vrf_read_rate * consts::numLanes
    );
  }

  void step(int lane_idx, bool is_write, const svBitVecVal *read_valid) {
    // TODO: consider laneIdx
    total_records++;
    vrf_write_records += (int64_t) is_write;
    vrf_read_records += (int64_t) std::min(__builtin_popcount(*read_valid), 2);  // 2 is num of regfile read ports
  }
};

struct ALUPerf {
  int64_t total_records = 0;
  int64_t adder_occupied_records = 0;
  int64_t shifter_occupied_records = 0;
  int64_t multiplier_occupied_records = 0;
  int64_t divider_occupied_records = 0;

  void print_summary(std::ostream &os) const {
    double adder_occupied_rate = (double) adder_occupied_records / (double) total_records;
    double shifter_occupied_rate = (double) shifter_occupied_records / (double) total_records;
    double multiplier_occupied_rate = (double) multiplier_occupied_records / (double) total_records;
    double divider_occupied_rate = (double) divider_occupied_records / (double) total_records;
    auto num_lanes = consts::numLanes;
    os << fmt::format(
        "adder_occupied: {}/{} ({:.3f}%) ({:.3f} ops per cycle)\n"
        "shifter_occupied: {}/{} ({:.3f}%) ({:.3f} ops per cycle)\n"
        "multiplier_occupied: {}/{} ({:.3f}%) ({:.3f} ops per cycle)\n"
        "divider_occupied: {}/{} ({:.3f}%) ({:.3f} ops per cycle)\n",
        adder_occupied_records, total_records, adder_occupied_rate * 100., adder_occupied_rate * num_lanes,
        shifter_occupied_records, total_records, shifter_occupied_rate * 100., shifter_occupied_rate * num_lanes,
        multiplier_occupied_records, total_records, multiplier_occupied_rate * 100., multiplier_occupied_rate * num_lanes,
        divider_occupied_records, total_records, divider_occupied_rate * 100., divider_occupied_rate * num_lanes
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

struct SingleLSUPerf {
  int pending_req_num;
  int64_t total_records;
  int64_t total_pending_reqs;
  int64_t total_occupied_lsu;
  int64_t total_req_num;

  bool last_a_valid, last_d_ready, last_d_valid, last_a_ready;

  void print_summary(std::ostream &os, int bank_idx) const {
    os << fmt::format(
        "bank_{}_avg_pending_req: {}/{} ({:.3f})\n"
        "bank_{}_occupied: {}/{} ({:.3f}%)\n"
        "bank_{}_requests: {}/{} ({:.3f}%)\n",
        bank_idx, total_pending_reqs, total_records, (double) total_pending_reqs / (double) total_records,
        bank_idx, total_occupied_lsu, total_records, (double) total_occupied_lsu / (double) total_records * 100.,
        bank_idx, total_req_num, total_records, (double) total_req_num / (double) total_records * 100.
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
      total_req_num ++;
    }
    total_records ++;
    total_pending_reqs += pending_req_num;
    total_occupied_lsu += (int64_t) pending_req_num != 0;
  }
};

struct LSUPerf {
  SingleLSUPerf perfs[consts::numTL] {};

  int64_t either_busy_records = 0;
  int64_t total_records = 0;

  void print_summary(std::ostream &os) const {
    for (auto i = 0; i < consts::numTL; i++) {
      perfs[i].print_summary(os, i);
    }
    os << fmt::format(
      "either_bank_busy: {}/{} ({:.3f}%)\n",
      either_busy_records, total_records, 100 * (double) either_busy_records / (double) total_records
    );
  }

  void peek_tl(bool a_valid, bool d_ready, int i) {
    perfs[i].peek_tl(a_valid, d_ready);
  }

  void poke_tl(bool d_valid, bool a_ready, int i) {
    perfs[i].poke_tl(d_valid, a_ready);
  }

  void step(int i) {
    perfs[i].step();

    bool finalized = true;  // finalized when all total_records are equal
    for (int j = 1; j < consts::numTL; j++) {
      if (perfs[j].total_records != perfs[0].total_records) {
        finalized = false;
      }
    }
    if (finalized) {
      total_records ++;
      bool is_busy = false;
      for (int j = 0; j < consts::numTL; j++) {
        if (perfs[j].pending_req_num > 0) {
          is_busy = true;
        }
      }
      either_busy_records += is_busy;
    }
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
