{ cmake
, ninja
, llvmPackages
, python3
, python3Packages
, fetchFromGitHub
}:
let
  stdenv = llvmPackages.stdenv;
  version = "3.4.0rc20250326";
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
  # iree-torch-mlir-version = "eefc553ffca45fd2432641918a73b810f64bba39";
  # iree-torch-mlir-src = fetchFromGitHub {
  #   owner = "iree-org";
  #   repo = "torch-mlir";
  #   rev = iree-torch-mlir-version;
  #   hash = "sha256-nVlndB0ccqyKlthZp7vQ4y7WlN/NRI4BaOMUIJfMeao=";
  # };
in
stdenv.mkDerivation {
  pname = "iree";
  version = version;

  src = fetchFromGitHub {
    owner = "iree-org";
    repo = "iree";
    tag = "iree-${version}";
    hash = "sha256-SnFxe91TgQffL8N+U8P0Fpk6i+3PqS7ssWV6GiwTxjA=";
  };

  postUnpack = ''
    cp -r ${iree-llvm-src}/* $sourceRoot/third_party/llvm-project/
    cp -r ${iree-cpuinfo-src}/* $sourceRoot/third_party/cpuinfo/
    cp -r ${iree-googletest-src}/* $sourceRoot/third_party/googletest/
    cp -r ${iree-benchmark-src}/* $sourceRoot/third_party/benchmark/
    cp -r ${iree-flatcc-src}/* $sourceRoot/third_party/flatcc/
    chmod -R u+w $sourceRoot/third_party/
  '';

  env.CMAKE_PREFIX_PATH = "${python3Packages.nanobind}/${python3.sitePackages}/nanobind";

  nativeBuildInputs = [
    cmake
    ninja
    python3
  ];

  doCheck = false;

  cmakeFlags = [
    "-DIREE_BUILD_TESTS=OFF"
    "-DIREE_BUILD_SAMPLES=OFF"
    "-DIREE_TARGET_BACKEND_DEFAULTS=OFF"
    "-DIREE_TARGET_BACKEND_LLVM_CPU=ON"
    "-DIREE_HAL_DRIVER_DEFAULTS=OFF"
    "-DIREE_INPUT_TOSA=OFF"
    "-DIREE_INPUT_STABLEHLO=OFF"
    "-DIREE_INPUT_TORCH=OFF"
    "-DIREE_INPUT_TOSA=OFF"
  ];

  patches = [
    ../patches/iree/0001-Find-nanobind-first.patch
  ];
}
