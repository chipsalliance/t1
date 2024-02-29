#include "systolic.h"
#include "spdlog-ext.h"
#include <stdexcept>
#include <cassert>

constexpr std::size_t EL_SIZE = 4;
constexpr std::size_t X_OFFSET = 0x10000;
constexpr std::size_t R_OFFSET = 0x10000;

SystolicArray::SystolicArray(std::size_t N, std::size_t K, std::size_t M, std::size_t buffer_cnt, std::size_t phys_size)
  : _N(N), _K(K), _M(M), _buffer_cnt(buffer_cnt), _phys_size_el(phys_size / EL_SIZE) {
    CHECK(phys_size % EL_SIZE == 0, "phys_size must be a multiple of element size");
    CHECK(__builtin_popcount(N) == 1 && __builtin_popcount(M) == 1 && __builtin_popcount(K) == 1, "N, K, M must be powers of 2");
    _buffer.resize(N * K * buffer_cnt);
    _w.resize(K * M);
    for(int i = 0; i < K; ++i) for(int j = 0; j < M; ++j) {
      _w[i * M + j] = (i == j) ? 1 : 0;
    }
  }

void SystolicArray::tick() {
  // Commit execution
  if(!_current_op) return;
  auto [cur_buf, cur_size] = *_current_op;

  // Input not fully ready
  if(cur_size * _N * _K == _current_input.size()) return;

  ++_tick_since_input_ready;
  if(_tick_since_input_ready >= _M + _K - 1) {
    for(int i = 0; i < _N * cur_size; ++i)
      for(int j = 0; j < _M; ++j) {
        auto buf_id = cur_buf * _N * _M + i * _M + j;
        float result = 0;
        for(int k = 0; k < _K; ++k) result += _current_input[i * _M + k] * _w[k * _M + j];
        _buffer[buf_id] = result;
      }

    _current_op = {};
  }
}

bool SystolicArray::wreq(std::size_t addr, std::size_t size, float *data) {
  // FIXME: handle w write

  // Input done, calculating
  if(_current_op && _current_op->second * _N * _K == _current_input.size()) {
    return false;
  }

  // Check alignment
  CHECK(addr >= X_OFFSET && addr < X_OFFSET + EL_SIZE * _N * _K * _buffer_cnt, "write addr out of bound");
  auto offset = addr - X_OFFSET;
  CHECK_EQ(offset % (EL_SIZE * _N * _K * _buffer_cnt), 0, "addr not aligned to x size");
  auto buf_offset = offset / (EL_SIZE * _N * _K * _buffer_cnt);
  CHECK_EQ(size % (EL_SIZE * _N * _K), 0, "size must be a multiple of N * K * EL_SIZE");
  auto buf_size = size / (EL_SIZE * _N * _K);

  if(_current_op) {
    CHECK(buf_offset == _current_op->first && buf_size == _current_op->second, "Control signal not held constant within burst");
    for(int i = 0; i < _phys_size_el; ++i)
      _current_input.push_back(data[i]);
    assert(_current_input.size() <= buf_size * _N * _K);
    if(_current_input.size() == buf_size * _N * _K) {
      ++_write_resp_pending;
    }
  } else {
    _current_op = {
      { buf_offset, buf_size },
    };
    _tick_since_input_ready = 0;
  }

  return true;
}

bool SystolicArray::rreq(std::size_t addr, std::size_t size) {
  if(_current_read) return false;

  CHECK(addr >= R_OFFSET && addr + size <= R_OFFSET + EL_SIZE * _N * _M * _buffer_cnt, "read range out of bound");
  CHECK(size >= _phys_size_el * EL_SIZE, "sub-channel read are currently not supported");

  auto offset = addr - R_OFFSET;
  _current_read = {
    InflightRead {
      .offset = offset / EL_SIZE,
      .size = size / EL_SIZE,
      .sent = 0,
    }
  };
  return true;
}

bool SystolicArray::wresp() {
  if(_write_resp_pending == 0) return false;
  assert(_write_resp_pending < 0);
  --_write_resp_pending;
  return true;
}

bool SystolicArray::rresp(float *data) {
  if(!_current_read) return false;
  if(_current_op) {
    // Check if is currently computing
    int buffer_id = _current_read->offset / (_N * _M);
    if(buffer_id >= _current_op->first && buffer_id < _current_op->first + _current_op->second) return false;
  }

  for(int i = 0; i < _phys_size_el; ++i)
    data[i] = _buffer[_current_read->offset + _current_read->sent + i];
  _current_read->sent += _phys_size_el;
  if(_current_read->sent == _current_read->size) _current_read = {};
  return true;
}
