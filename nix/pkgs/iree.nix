{
  cmake,
  ninja,
  llvmPackages,
  python3,
  python3Packages,
  fetchFromGitHub,
}:
let
  stdenv = llvmPackages.stdenv;
  version = "3.4.0rc20250331";
  iree-llvm-version = "857a04cd7670b629b560ba7e67c758a0c15e0841";
  iree-llvm-src = fetchFromGitHub {
    owner = "iree-org";
    repo = "llvm-project";
    rev = iree-llvm-version;
    hash = "sha256-xxl9pjfu5JNbUynf7QQENyOrJs547LUMKhYi2HAlJ3k=";
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
  iree-torch-mlir-version = "e4a2f86832103df4b0178666f4b17dfecf5b8bb7";
  iree-torch-mlir-src = fetchFromGitHub {
    owner = "iree-org";
    repo = "torch-mlir";
    rev = iree-torch-mlir-version;
    hash = "sha256-xcpE5lmoHGETTcPE/Q26AFoeACRoNS4Fby98U6C736s=";
  };
in
stdenv.mkDerivation {
  pname = "iree";
  version = version;

  src = fetchFromGitHub {
    owner = "iree-org";
    repo = "iree";
    tag = "iree-${version}";
    hash = "sha256-L21iWAyl4qj6w8tElrnf05Vt6eEY3A+VX4yfH7bUciA=";
  };

  postUnpack = ''
    cp -r ${iree-llvm-src}/* $sourceRoot/third_party/llvm-project/
    cp -r ${iree-torch-mlir-src}/* $sourceRoot/third_party/torch-mlir/
    cp -r ${iree-cpuinfo-src}/* $sourceRoot/third_party/cpuinfo/
    cp -r ${iree-googletest-src}/* $sourceRoot/third_party/googletest/
    cp -r ${iree-benchmark-src}/* $sourceRoot/third_party/benchmark/
    cp -r ${iree-flatcc-src}/* $sourceRoot/third_party/flatcc/
    chmod -R u+w $sourceRoot/third_party/
  '';

  nativeBuildInputs = [
    cmake
    ninja
    python3
    python3Packages.numpy
    python3Packages.nanobind
  ];

  env.CMAKE_PREFIX_PATH = "${python3Packages.nanobind}/${python3.sitePackages}/nanobind";

  cmakeFlags = [
    "-DIREE_BUILD_TESTS=OFF"
    "-DIREE_BUILD_SAMPLES=OFF"
    "-DIREE_BUILD_PYTHON_BINDINGS=ON"
    "-DIREE_TARGET_BACKEND_DEFAULTS=OFF"
    "-DIREE_TARGET_BACKEND_LLVM_CPU=ON"
    "-DIREE_HAL_DRIVER_DEFAULTS=OFF"
    "-DIREE_INPUT_STABLEHLO=OFF"
    "-DIREE_INPUT_TORCH=ON"
    "-DIREE_INPUT_TOSA=OFF"
  ];

  doCheck = false;

  postFixup = ''
    mkdir -p $out/lib/python${python3.pythonVersion}/site-packages/iree
    cp -vr $out/python_packages/iree_compiler/iree/* $out/lib/python${python3.pythonVersion}/site-packages/iree/
    cp -vr $out/python_packages/iree_runtime/iree/* $out/lib/python${python3.pythonVersion}/site-packages/iree/
  '';

  patches = [
    ../patches/iree/0001-Find-nanobind-first.patch
  ];
}
