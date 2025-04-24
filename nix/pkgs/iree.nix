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
  version = "3.7.0rc20250729";
  iree-llvm-version = "1381ad497b9a6d3da630cbef53cbfa9ddf117bb6";
  iree-llvm-src = fetchFromGitHub {
    owner = "iree-org";
    repo = "llvm-project";
    rev = iree-llvm-version;
    hash = "sha256-elR+PmdT4takpe7EfepR+G8THJKCeTvFn8aAMn2F2mA=";
  };
  iree-cpuinfo-version = "3c8b1533ac03dd6531ab6e7b9245d488f13a82a5";
  iree-cpuinfo-src = fetchFromGitHub {
    owner = "pytorch";
    repo = "cpuinfo";
    rev = iree-cpuinfo-version;
    hash = "sha256-eshoHmGiu5k0XE/A1SWf7OvBj7/YD9JNSZgoyGzGcLA=";
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
  iree-torch-mlir-version = "1983b6db9cdba2dfef4939a4fb521e9ac25dc08e";
  iree-torch-mlir-src = fetchFromGitHub {
    owner = "iree-org";
    repo = "torch-mlir";
    rev = iree-torch-mlir-version;
    hash = "sha256-6cpeFnn2qxPniRyBFOnkx1q805ioVpdTIjCf0xmOyrs=";
  };
in
stdenv.mkDerivation {
  pname = "iree";
  version = version;

  src = fetchFromGitHub {
    owner = "iree-org";
    repo = "iree";
    tag = "iree-${version}";
    hash = "sha256-G/I4ShB+vRLhQTgcjNBuXVVRLkbDXRwUA2g3oamDFps=";
  };

  postUnpack = ''
    cp -r ${iree-llvm-src}/* $sourceRoot/third_party/llvm-project/
    cp -r ${iree-torch-mlir-src}/* $sourceRoot/third_party/torch-mlir/
    cp -r ${iree-cpuinfo-src}/* $sourceRoot/third_party/cpuinfo/
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
    "-DIREE_USE_SYSTEM_DEPS=ON"
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
}
