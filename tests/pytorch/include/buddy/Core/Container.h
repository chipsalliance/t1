//===- Container.h --------------------------------------------------------===//
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
// Container descriptor.
//
//===----------------------------------------------------------------------===//
//
//===----------------------------------------------------------------------===//
//
// This is vendored version of buddy-mlir Container.h file at revision
// c57584a0e3c38e938a3902320f62b202ced84996. Modified for T1 embedded test env.
//
//===----------------------------------------------------------------------===//

#include <cstdint>
#include <vector>

// MemRef descriptor.
// - T represents the type of the elements.
// - N represents the number of dimensions.
// - The storage order is NCHW.
template <typename T, size_t N> class MemRef {
public:
  // Construct using init to allocated area
  constexpr MemRef(T *allocated, T init, intptr_t sizes[N]);
  constexpr MemRef(T *allocated, intptr_t sizes[N], intptr_t offset = 0);
  // Get the data pointer.
  T *getData();
  // Get the sizes (shape).
  const intptr_t *getSizes() { return sizes; }
  // Get the strides.
  const intptr_t *getStrides() { return strides; }
  // Get the rank of the memref.
  size_t getRank() const { return N; }
  // Get the size (number of elements).
  size_t getSize() const { return product(this->sizes); }
  // Get the element at index.
  const T &operator[](size_t index) const;
  T &operator[](size_t index);

protected:
  // Set the strides.
  // Computes the strides of the transposed tensor for transpose=true.
  inline void setStrides();
  // Compute the product of array elements.
  inline size_t product(const intptr_t sizes[N]) const;

  // Data.
  // The `aligned` and `allocated` members point to the same address, `aligned`
  // member is responsible for handling data, and `allocated` member is
  // resposible for handling the memory space.
  T *allocated = nullptr;
  T *aligned = nullptr;
  // Offset.
  intptr_t offset = 0;
  // Shape.
  intptr_t sizes[N];
  // Strides.
  intptr_t strides[N];
};

template <typename T, std::size_t N>
constexpr MemRef<T, N>::MemRef(T *allocated, T init, intptr_t sizes[N])
    : MemRef(allocated, sizes) {
  size_t size = product(sizes);
  std::fill(aligned, aligned + size, init);
}

// MemRef Array Constructor.
// Construct a MemRef object from the data pointer, sizes, and offset.
// The default offset is 0.
template <typename T, std::size_t N>
constexpr MemRef<T, N>::MemRef(T *data, intptr_t sizes[N], intptr_t offset) {
  this->offset = offset;
  for (size_t i = 0; i < N; i++) {
    this->sizes[i] = sizes[i];
  }
  setStrides();
  size_t size = product(sizes);
  allocated = data;
  aligned = allocated;
  for (size_t i = 0; i < size; i++) {
    aligned[i] = data[i];
  }
}

// Get the data pointer.
// Return the `aligned` pointer if the container data size is greater than zero.
// If the data size is negative or zero, which means no space is allocated for
// the container data pointer, the function does not allow to return the data
// pointer.
template <typename T, std::size_t N> T *MemRef<T, N>::getData() {
  size_t size = product(this->sizes);
  return aligned;
}

// Get the element at index.
// Return the specific element if the container data size is greater than zero.
// If the data size is negative or zero, which means no space is allocated for
// the container data pointer, this operator does not allow to return the data
// element.
template <typename T, std::size_t N>
const T &MemRef<T, N>::operator[](size_t index) const {
  size_t size = product(this->sizes);
  return aligned[index + offset];
}

template <typename T, std::size_t N> T &MemRef<T, N>::operator[](size_t index) {
  size_t size = product(this->sizes);
  return aligned[index + offset];
}

// Calculate the stride values for each dimension based on the sizes.
template <typename T, std::size_t N> inline void MemRef<T, N>::setStrides() {
  strides[N - 1] = 1;
  if (N < 2)
    return;
  // Prevent implicit conversions between unsigned and signed
  for (std::size_t i = N - 1; i > 0; i--) {
    strides[i - 1] = strides[i] * sizes[i];
  }
}

// Calculate the total number of elements in the MemRef container.
template <typename T, std::size_t N>
inline size_t MemRef<T, N>::product(const intptr_t sizes[N]) const {
  size_t size = 1;
  for (size_t i = 0; i < N; i++)
    size *= sizes[i];
  return size;
}
