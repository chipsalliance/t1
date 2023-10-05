#include <bit>
#include <cstdint>
#include <fstream>

#include <nlohmann/json.hpp>
using json = nlohmann::json;

#include "rtl_config.h"
#include "spdlog-ext.h"

RTLConfig::RTLConfig(const char *json_file_name) {
  std::ifstream json_file(json_file_name);
  json root = json::parse(json_file);
  const auto &para = root["design"]["parameter"];
  para["xLen"].get_to(x_len);
  para["vLen"].get_to(v_len);
  v_len_in_bytes = v_len / 8;
  para["datapathWidth"].get_to(datapath_width);
  datapath_width_in_bytes = datapath_width / 8;
  datapath_width_log2 = std::__bit_width(datapath_width_in_bytes) - 1;
  para["laneNumber"].get_to(lane_number);
  para["physicalAddressWidth"].get_to(physical_address_width);
  para["chainingSize"].get_to(chaining_size);
  para["vrfWriteQueueSize"].get_to(vrf_write_queue_size);
}

size_t lsu_idx_default = 255;
