import torch
import torch._dynamo as dynamo
from torch._inductor.decomposition import decompositions as inductor_decomp

from buddy.compiler.frontend import DynamoCompiler
from buddy.compiler.ops import tosa

# Define the input data.
float32_in1 = torch.randn(8, 8, 8).to(torch.float32)
float32_in2 = torch.randn(8, 8, 8).to(torch.float32)

# Initialize the dynamo compiler.
dynamo_compiler = DynamoCompiler(
    primary_registry=tosa.ops_registry,
    aot_autograd_decomposition=inductor_decomp,
)

# Pass the function and input data to the dynamo compiler's importer, the 
# importer will first build a graph. Then, lower the graph to top-level IR. 
# (tosa, linalg, etc.). Finally, accepts the generated module and weight parameters.
graphs = dynamo_compiler.importer(torch.matmul, *(float32_in1, float32_in2))
graph = graphs[0]
graph.lower_to_top_level_ir()

with open("forward.mlir", "w") as mlir_module:
    print(graph._imported_module, file = mlir_module)
