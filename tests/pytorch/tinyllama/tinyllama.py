# ===- buddy_tinyllama_import.py -----------------------------------------------
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# ===---------------------------------------------------------------------------
#
# This is the TinyLlama model AOT importer.
#
# ===---------------------------------------------------------------------------

import os
import sys
import torch
from torch._inductor.decomposition import decompositions as inductor_decomp
from transformers import AutoModelForCausalLM, AutoTokenizer

from buddy.compiler.frontend import DynamoCompiler
from buddy.compiler.ops import tosa
from buddy.compiler.graph import GraphDriver
from buddy.compiler.graph.transform import simply_fuse

checkpoint = os.environ.get("LLAMA_MODEL_PATH")
if checkpoint is None:
    sys.exit("Error: No model path was provided. Please set $LLAMA_MODEL_PATH")
tokenizer = AutoTokenizer.from_pretrained(checkpoint)
model = AutoModelForCausalLM.from_pretrained(checkpoint, device_map="auto")
model.config.use_cache = False

# Initialize Dynamo Compiler with specific configurations as an importer.
dynamo_compiler = DynamoCompiler(
    primary_registry=tosa.ops_registry,
    aot_autograd_decomposition=inductor_decomp,
)

# Import the model into MLIR module and parameters.
with torch.no_grad():
    data = torch.tensor([[1 for i in range(40)]], dtype=torch.int64)
    graphs = dynamo_compiler.importer(model, data)

assert len(graphs) == 1
graph = graphs[0]
params = dynamo_compiler.imported_params[graph]
pattern_list = [simply_fuse]
graphs[0].fuse_ops(pattern_list)
driver = GraphDriver(graphs[0])
driver.subgraphs[0].lower_to_top_level_ir()
with open("subgraphs0.mlir", "w") as module_file:
    print(driver.subgraphs[0]._imported_module, file=module_file)
with open("forward.mlir", "w") as module_file:
    print(driver.construct_main_graph(True), file=module_file)
