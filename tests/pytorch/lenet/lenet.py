import os
import sys
from pathlib import Path

import numpy as np
import torch
from torch._inductor.decomposition import decompositions as inductor_decomp
import torch._inductor.lowering

from buddy.compiler.frontend import DynamoCompiler
from buddy.compiler.graph import GraphDriver
from buddy.compiler.graph.transform import simply_fuse
from buddy.compiler.ops import tosa
from model import LeNet

def main():
    model_path = os.environ.get("LENET_MODEL_PATH")
    if model_path is None:
        sys.exit("Error: No model path was provided. Please set $LENET_MODEL_PATH")
    model = torch.load(model_path)
    model = model.eval()

    # Initialize Dynamo Compiler with specific configurations as an importer.
    dynamo_compiler = DynamoCompiler(
        primary_registry=tosa.ops_registry,
        aot_autograd_decomposition=inductor_decomp,
    )

    data = torch.randn([1, 1, 28, 28])
    # Import the model into MLIR module and parameters.
    with torch.no_grad():
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

if __name__ == "__main__":
    main()
