#pragma once

struct RTLConfig {
  explicit RTLConfig(const char *json_file_name);

  size_t x_len;
  size_t v_len;
  size_t v_len_in_bytes;
  size_t datapath_width;
  size_t lane_number;
  size_t physical_address_width;
  size_t chaining_size;
  size_t vrf_write_queue_size;

  size_t elen = 32;
  size_t vreg_number = 32;
  size_t mshr_number = 3;
  size_t tl_bank_number = 2;
};

extern size_t lsu_idx_default;