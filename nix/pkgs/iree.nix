{ cmake
, ninja
, llvmPackages
, python3
, fetchFromGitHub
}:
let
  stdenv = llvmPackages.stdenv;
  version = "v3.2.0";
  iree-llvm-version = "8cb4b3e21e03d3e029ade27139eab1a25720c773";
  iree-llvm-src = fetchFromGitHub {
    owner = "iree-org";
    repo = "llvm-project";
    rev = iree-llvm-version;
    hash = "sha256-AUvbHbSIB1hlVFKwKICq+xgcGi4vblW6Hxo3anyKfJ0=";
  };
  iree-stablehlo-version = "8993ef703e5f98baa0da56346621e07932c68d8c";
  iree-stablehlo-src = fetchFromGitHub {
    owner = "iree-org";
    repo = "stablehlo";
    rev = iree-stablehlo-version;
    hash = "sha256-GZsNrOPQ2m8D9wdTt8zGUVxdMymLFtsTXY02JIqIP3E=";
  };
  iree-cpuinfo-version = "3c8b1533ac03dd6531ab6e7b9245d488f13a82a5";
  iree-cpuinfo-src = fetchFromGitHub {
    owner = "pytorch";
    repo = "cpuinfo";
    rev = iree-cpuinfo-version;
    hash = "sha256-eshoHmGiu5k0XE/A1SWf7OvBj7/YD9JNSZgoyGzGcLA=";
  };
  iree-googletest-version = "c8393f8554419dc27b688c535b8fa4afb82146a4";
  iree-googletest-src = fetchFromGitHub {
    owner = "google";
    repo = "googletest";
    rev = iree-googletest-version;
    hash = "sha256-K48kVGMyoWWcDHXhHM98NORZerBm6wbnYf50GkK4B2Y=";
  };
  iree-benchmark-version = "99bdb2127d1fa1cff444bbefb814e105c7d20c45";
  iree-benchmark-src = fetchFromGitHub {
    owner = "google";
    repo = "benchmark";
    rev = iree-benchmark-version;
    hash = "sha256-d/7BDynAUsH20bGqyh4HLKKgqCeGlTRQRvqX5dmpMLg=";
  };
  iree-flatcc-version = "9362cd00f0007d8cbee7bff86e90fb4b6b227ff3";
  iree-flatcc-src = fetchFromGitHub {
    owner = "dvidelabs";
    repo = "flatcc";
    rev = iree-flatcc-version;
    hash = "sha256-umZ9TvNYDZtF/mNwQUGuhAGve0kPw7uXkaaQX0EzkBY=";
  };
  iree-torch-mlir-version = "eefc553ffca45fd2432641918a73b810f64bba39";
  iree-torch-mlir-src = fetchFromGitHub {
    owner = "iree-org";
    repo = "torch-mlir";
    rev = iree-torch-mlir-version;
    hash = "sha256-nVlndB0ccqyKlthZp7vQ4y7WlN/NRI4BaOMUIJfMeao=";
  };
in
stdenv.mkDerivation {
  pname = "iree";
  version = version;

  src = fetchFromGitHub {
    owner = "iree-org";
    repo = "iree";
    tag = version;
    hash = "sha256-gS65v/iVCmR5yr4SYZIPnUCndRPvdqa83ShoEKywXeo=";
  };

  postUnpack = ''
    cp -r ${iree-llvm-src}/* $sourceRoot/third_party/llvm-project/
    cp -r ${iree-stablehlo-src}/* $sourceRoot/third_party/stablehlo/
    cp -r ${iree-cpuinfo-src}/* $sourceRoot/third_party/cpuinfo/
    cp -r ${iree-googletest-src}/* $sourceRoot/third_party/googletest/
    cp -r ${iree-benchmark-src}/* $sourceRoot/third_party/benchmark/
    cp -r ${iree-flatcc-src}/* $sourceRoot/third_party/flatcc/
    cp -r ${iree-torch-mlir-src}/* $sourceRoot/third_party/torch-mlir/
    chmod -R u+w $sourceRoot/third_party/
  '';

  buildInputs = [ cmake ninja python3 ];

  doCheck = false;

  cmakeFlags = [
    "-DIREE_BUILD_TESTS=OFF"
    "-DIREE_BUILD_SAMPLES=OFF"
    "-DIREE_TARGET_BACKEND_LLVM_CPU_WASM=OFF"
    "-DIREE_TARGET_BACKEND_METAL_SPIRV=OFF"
    "-DIREE_TARGET_BACKEND_VULKAN_SPIRV=OFF"
    "-DIREE_HAL_DRIVER_CUDA=OFF"
    "-DIREE_HAL_DRIVER_HIP=OFF"
    "-DIREE_HAL_DRIVER_METAL=OFF"
    "-DIREE_HAL_DRIVER_VULKAN=OFF"
  ];

  patches = [
    ../patches/iree/0001-Find-nanobind-first.patch
  ];
}
