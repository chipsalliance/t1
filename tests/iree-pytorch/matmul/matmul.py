import torch
import torch.nn as nn
import iree.turbine.aot as aot


class ToyModel(nn.Module):
    def __init__(self):
        super().__init__()

    def forward(self, arg0: torch.Tensor, arg1: torch.tensor) -> torch.Tensor:
        return torch.matmul(arg0, arg1)


model = ToyModel()
example_inputs = (
    torch.tensor([[1, 2], [3, 4]], dtype=torch.int32),
    torch.tensor([[1, 2], [3, 4]], dtype=torch.int32),
)

exported = aot.export(model, *example_inputs)
exported.save_mlir("matmul.mlir")
