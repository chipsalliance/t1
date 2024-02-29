#include <vector>
#include <optional>
#include <cstdint>

struct InflightRead {
  // In element
  std::size_t offset;
  // In element
  std::size_t size;
  // Also in element
  std::size_t sent;
};

// Please call with the following order:
// wresp, rresp, wreq, rreq, tick
// Please only call rresp and wresp once. Internal counters will change.
// TODO: if that cannot be done, additional logic is needed
//
// Offsets: w = 0, x = 0x10000, result = 0x20000
class SystolicArray {
public:
  SystolicArray(
    std::size_t N, std::size_t K, std::size_t M, std::size_t buffer_cnt,
    std::size_t base_addr, std::size_t phys_size); // phys_size in bytes

  void tick();
  bool wreq(std::size_t addr, std::size_t size, float *data);
  bool wresp();
  bool rreq(std::size_t addr, std::size_t size);
  bool rresp(float *data);

private:
  std::size_t _N, _K, _M, _buffer_cnt, _base_addr, _phys_size_el;

  // (buffer, buffer_cnt)
  std::optional<std::pair<std::size_t, std::size_t>> _current_op = {};
  std::optional<InflightRead> _current_read = {};

  std::size_t _tick_since_input_ready;
  std::size_t _write_resp_pending = 0;
  std::vector<float> _current_input;
  std::vector<float> _buffer;
  std::vector<float> _w;
};
