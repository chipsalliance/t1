import torch
import torch._dynamo as dynamo
from torch._inductor.decomposition import decompositions as inductor_decomp

from buddy.compiler.frontend import DynamoCompiler
from buddy.compiler.ops import tosa

def main():
    float32_in1 = torch.randn(8, 8, 8).to(torch.float32)
    float32_in2 = torch.randn(8, 8, 8).to(torch.float32)

    dynamo_compiler = DynamoCompiler(
        primary_registry=tosa.ops_registry,
        aot_autograd_decomposition=inductor_decomp,
    )

    graphs = dynamo_compiler.importer(torch.matmul, *(float32_in1, float32_in2))
    graph = graphs[0]
    graph.lower_to_top_level_ir()

    print(graph._imported_module)

if __name__ == "__main__":
    main()
