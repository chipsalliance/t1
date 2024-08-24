import torch
import torch._dynamo as dynamo
from torch._inductor.decomposition import decompositions as inductor_decomp

from buddy.compiler.frontend import DynamoCompiler
from buddy.compiler.ops import tosa

# Define the target function or model.
def foo(x, y):
    return x * y + x

def main():
    # Define the input data.
    float32_in1 = torch.randn(512).to(torch.float32)
    float32_in2 = torch.randn(512).to(torch.float32)

    # Initialize the dynamo compiler.
    dynamo_compiler = DynamoCompiler(
        primary_registry=tosa.ops_registry,
        aot_autograd_decomposition=inductor_decomp,
    )

    # Pass the function and input data to the dynamo compiler's importer, the 
    # importer will first build a graph. Then, lower the graph to top-level IR. 
    # (tosa, linalg, etc.). Finally, accepts the generated module and weight parameters.
    graphs = dynamo_compiler.importer(foo, *(float32_in1, float32_in2))
    graph = graphs[0]
    graph.lower_to_top_level_ir()

    print(graph._imported_module)

if __name__ == "__main__":
    main()
