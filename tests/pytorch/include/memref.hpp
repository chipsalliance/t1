//===- ImgContainer.h -----------------------------------------------------===//
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
//===----------------------------------------------------------------------===//
//
// Original source from https://github.com/buddy-compiler/buddy-mlir.
// Modified for embedded environment.
//
//===----------------------------------------------------------------------===//

#ifndef MEMREF_H
#define MEMREF_H

#include <cstddef>
#include <cstdint>

template <typename T, size_t N> class MemRef {
public:
  constexpr MemRef(T *data, const int32_t sizes[N]);
  constexpr MemRef(T *data, T init, const int32_t sizes[N]);

protected:
  inline void setStrides();

  // https://github.com/llvm/llvm-project/blob/a50b9633357007ff886f3fd228ca4b8a9b9b9852/mlir/lib/Conversion/LLVMCommon/TypeConverter.cpp#L401
  T *allocated = nullptr;
  T *aligned = nullptr;
  int32_t offset = 0;
  int32_t sizes[N];
  int32_t strides[N];
};

template <typename T, std::size_t N>
constexpr MemRef<T, N>::MemRef(T *data, const int32_t sizes[N]) {
  for (size_t i = 0; i < N; i++) {
    this->sizes[i] = sizes[i];
  }

  setStrides();

  allocated = data;
  aligned = data;
}

template <typename T, std::size_t N>
constexpr MemRef<T, N>::MemRef(T *data, T init, const int32_t sizes[N])
    : MemRef(data, sizes) {

  int32_t total_size = 0;
  for (size_t i = 0; i < N; i++) {
    total_size += sizes[i];
  }

  for (int32_t i = 0; i < total_size; i++) {
    aligned[i] = init;
  }
}

template <typename T, std::size_t N> inline void MemRef<T, N>::setStrides() {
  strides[N - 1] = 1;
  if (N < 2)
    return;

  for (std::size_t i = N - 1; i > 0; i--) {
    strides[i - 1] = strides[i] * sizes[i];
  }
}

#endif // MEMREF_H
