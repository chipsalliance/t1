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

#include <buddy/Core/Container.h>
#include <cstdio>

#define PARAMS_SIZE 673
#define MAX_VOCAB_SIZE 320
#define MAX_TOKEN_LENGTH 40
#define HIDDEN_SIZE 128

// resultContainer[0]
__attribute((
    section(".vdata"))) float result0[1 + MAX_TOKEN_LENGTH + HIDDEN_SIZE];
// resultContainer[1]
__attribute((
    section(".vdata"))) float result1[1 + MAX_TOKEN_LENGTH + MAX_VOCAB_SIZE];
// inputContainer
__attribute((section(".vdata"))) int32_t input[1 + MAX_TOKEN_LENGTH];
// paramsContainer
__attribute((section(".vdata"))) float param[PARAMS_SIZE];

extern "C" {
void _mlir_ciface_forward(MemRef<float, 3> *a, MemRef<float, 1> *b,
                          MemRef<int32_t, 2> *c);
}

extern "C" int test() {
  int32_t sizesResult0[3] = {1, MAX_TOKEN_LENGTH, HIDDEN_SIZE};
  int32_t sizesResult1[3] = {1, MAX_TOKEN_LENGTH, MAX_VOCAB_SIZE};

  int32_t sizesInput[2] = {1, MAX_TOKEN_LENGTH};
  MemRef<int32_t, 2> inputContainer(input, 4.0, sizesInput);

  MemRef<float, 3> resultContainer[2] = {
      MemRef<float, 3>(result0, 2.0, sizesResult0),
      MemRef<float, 3>(result1, 3.0, sizesResult1)};

  int32_t sizesParam[1] = {PARAMS_SIZE};
  MemRef<float, 1> paramsContainerf32(param, 5.0, sizesParam);

  _mlir_ciface_forward(resultContainer, &paramsContainerf32, &inputContainer);
  return 0;
}
