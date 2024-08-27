//===- GoogleBenchmarkMain.cpp --------------------------------------------===//
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
// This file implements the benchmark for Tiny LLaMA model.
//
//===----------------------------------------------------------------------===//

#include "memref.hpp"

constexpr size_t ParamsSize = 110581;
// constexpr size_t ParamsSize = 11058;
constexpr size_t MaxVocabSize = 32000;
constexpr size_t MaxTokenLength = 40;
constexpr size_t HiddenSize = 2048;

// resultContainer[0]
__attribute((section(".vdata"))) float result0[1 + MaxTokenLength + HiddenSize];
static constexpr int32_t sizesResult0[3] = {1, MaxTokenLength, HiddenSize};

// resultContainer[1]
__attribute((
    section(".vdata"))) float result1[1 + MaxTokenLength + MaxVocabSize];
static constexpr int32_t sizesResult1[3] = {1, MaxTokenLength, MaxVocabSize};

// inputContainer
__attribute((section(".vdata"))) int32_t input[1 + MaxTokenLength];
static constexpr int32_t sizesInput[2] = {1, MaxTokenLength};

// paramsContainer
__attribute((section(".vdata"))) float param[ParamsSize];
static constexpr int32_t sizesParam[1] = {ParamsSize};

extern "C" {
void _mlir_ciface_forward(MemRef<float, 3> *a, MemRef<float, 1> *b,
                          MemRef<int32_t, 2> *c);
}

MemRef<float, 3> resultContainer[2] = {
    MemRef<float, 3>(result0, 2.0, sizesResult0),
    MemRef<float, 3>(result1, 3.0, sizesResult1)};
MemRef<int32_t, 2> inputContainer(input, 4, sizesInput);
MemRef<float, 1> paramsContainerf32(param, 5.0, sizesParam);

extern "C" int test() {
  _mlir_ciface_forward(resultContainer, &paramsContainerf32, &inputContainer);
  return 0;
}
