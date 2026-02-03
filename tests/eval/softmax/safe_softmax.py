import torch
import torch.nn.functional as F
import argparse
import sys


def safe_softmax_with_scaling(act, d_head=128):
    """
    Safe softmax function. First scales the input: act * rsqrt(d_head).
    Input/Output are float32.
    """
    # Calculate scaling factor: 1/sqrt(d_head)
    scale = torch.rsqrt(torch.tensor(d_head, dtype=torch.float32))

    # Apply scaling: act * rsqrt(d_head)
    act_scaled = act * scale

    # Safe softmax implementation
    # Subtract max value for numerical stability
    max_vals = act_scaled.max(dim=-1, keepdim=True)[0]
    act_stable = act_scaled - max_vals

    # Calculate exponentials
    exp_act = torch.exp(act_stable)

    # Calculate denominator (sum)
    sum_exp = exp_act.sum(dim=-1, keepdim=True)

    # Calculate softmax
    softmax_result = exp_act / sum_exp

    return softmax_result


def write_tensor_to_file(
    f,
    var_name: str,
    tensor,
    attribute='__attribute((section(".vdata")))',
    guard: str | None = None,
):
    """
    Helper function to write a single tensor's data to an open file handle.
    """
    # Ensure data is numpy (Input is already float32)
    data = tensor.detach().cpu().numpy().flatten()

    f.write(f"// Tensor: {var_name}\n")
    f.write(f"const int {var_name}_dim = {tensor.dim()};\n")
    f.write(
        f"const int {var_name}_shape[] = {{ {', '.join(map(str, tensor.shape))} }};\n"
    )
    f.write(f"const int {var_name}_len = {data.size};\n")

    if guard != None:
        f.write(f"#ifdef {guard}\n")

    f.write(f"{attribute} float {var_name}[] = {{\n")

    # Write data in chunks
    for i, val in enumerate(data):
        f.write(f"{val:.8f}f")
        if i < data.size - 1:
            f.write(", ")
        if (i + 1) % 10 == 0:
            f.write("\n    ")

    f.write("\n};\n\n")

    if guard != None:
        f.write(f"#endif // {guard}\n")


def dump_tensors_to_header(tensors_list, d_head, file_name="tensor_data.h"):
    """
    Writes multiple tensors to a single C header file.
    Args:
        tensors_dict: Dict with format {'c_variable_name': torch_tensor}
        file_name: Output filename
    """
    header_guard = file_name.replace(".", "_").upper()

    with open(file_name, "w") as f:
        # Write Header Guard
        f.write(f"#ifndef {header_guard}\n")
        f.write(f"#define {header_guard}\n\n")

        f.write(f"const int d_head = {d_head};\n")

        for config in tensors_list:
            write_tensor_to_file(f, **config)

        # Close Header Guard
        f.write(f"#endif // {header_guard}\n")

    print(f"Successfully generated {file_name}")


def get_reference_implementation(act, d_head):
    """
    Calculates the Reference result using standard PyTorch ops (float32).
    """
    scale = 1.0 / torch.sqrt(torch.tensor(d_head, dtype=torch.float32))

    act_scaled = act * scale
    reference_scaled = F.softmax(act_scaled, dim=-1)

    return reference_scaled


# Main program
if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Differential Test and Data Dump Tool (Float32)"
    )
    parser.add_argument(
        "mode",
        choices=["test", "dump"],
        help="Select mode: 'test' for diff testing, 'dump' for C header generation",
    )
    args = parser.parse_args()

    # 1. Setup Common Data
    torch.manual_seed(42)
    d_head = 128

    act = torch.randn(32, 4096, dtype=torch.float32)

    # Generate reference for differential test.
    reference_scaled = get_reference_implementation(act, d_head)

    # 2. Execute Logic based on Mode
    if args.mode == "test":
        print(f"configuration: d_head={d_head}, shape={act.shape}, dtype={act.dtype}")

        # Run Custom Implementation
        result_custom = safe_softmax_with_scaling(act, d_head)

        # Compare
        diff = torch.abs(result_custom - reference_scaled).max()

        print(f"\nMax difference: {diff.item():.6e}")

        # Strict tolerance for float32
        if diff.item() < 1e-6:
            print("Status: Matches reference")
        else:
            print("Status: Difference too large")
            sys.exit(1)

    elif args.mode == "dump":
        print("=== Running Data Dump (Float32) ===")

        # Prepare dictionary of tensors to dump
        out = torch.zeros_like(act)
        tensors_to_dump = [
            {"var_name": "input_tensor", "tensor": act},
            {
                "var_name": "reference_tensor",
                "tensor": reference_scaled,
                "guard": "DO_DIFF_TEST",
            },
            {
                "var_name": "output_tensor",
                "tensor": out,
            },
        ]

        dump_tensors_to_header(tensors_to_dump, d_head, "model_data.h")
