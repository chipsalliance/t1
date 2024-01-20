#include <bit>
#include <fstream>

#include <nlohmann/json.hpp>
using json = nlohmann::json;

#include "rtl_config.h"
#include "spdlog-ext.h"

RTLConfig::RTLConfig(const char *json_file_name) {
  std::ifstream json_file(json_file_name);
  json root = json::parse(json_file);
  const auto &para = root["parameter"];
  x_len = para["xLen"];
  v_len = para["vLen"];
  v_len_in_bytes = v_len / 8;
  datapath_width = para["datapathWidth"];
  datapath_width_in_bytes = datapath_width / 8;
  datapath_width_log2 = std::__bit_width(datapath_width_in_bytes) - 1;
  lane_number = para["laneNumber"];
  physical_address_width = para["physicalAddressWidth"];
  chaining_size = para["chainingSize"];
  vrf_write_queue_size = para["vrfWriteQueueSize"];
  tl_bank_number = para["memoryBankSize"];
}

size_t lsu_idx_default = 255;
