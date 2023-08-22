#include <fstream>
#include <bit>

#include <json/json.h>

#include "rtl_config.h"

RTLConfig::RTLConfig(const char *json_file_name) {
  Json::Value root;
  std::ifstream(json_file_name) >> root;
  const auto &para = root["design"]["parameter"];
  x_len = para["xLen"].asUInt64();
  v_len = para["vLen"].asUInt64();
  v_len_in_bytes = v_len / 8;
  datapath_width = para["datapathWidth"].asUInt64();
  datapath_width_in_bytes = datapath_width / 8;
  datapath_width_log2 = std::__bit_width(datapath_width_in_bytes) - 1;
  lane_number = para["laneNumber"].asUInt64();
  physical_address_width = para["physicalAddressWidth"].asUInt64();
  chaining_size = para["chainingSize"].asUInt64();
  vrf_write_queue_size = para["vrfWriteQueueSize"].asUInt64();
}

size_t lsu_idx_default = 255;
