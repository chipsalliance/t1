{
  stdenv,
  fetchFromGitHub,
  cmake,
  python3,
  ninja,
}:

stdenv.mkDerivation rec {
  pname = "llvm-compiler-rt";
  version = "21.1.7";

  src = fetchFromGitHub {
    owner = "llvm";
    repo = "llvm-project";
    rev = "292dc2b86f66e39f4b85ec8b185fd8b60f5213ce";
    hash = "sha256-SaRJ7+iZMhhBdcUDuJpMAY4REQVhrvYMqI2aq3Kz08o=";
  };

  sourceRoot = "${src.name}/compiler-rt";

  nativeBuildInputs = [
    cmake
    python3
    ninja
  ];

  env = {
    NIX_CFLAGS_COMPILE = "-march=rv32imafc_zve32f_zvl256b -mabi=ilp32f";
  };

  cmakeFlags = [
    "-DCOMPILER_RT_BUILD_BUILTINS=ON"
    "-DCOMPILER_RT_BUILD_SANITIZERS=OFF"
    "-DCOMPILER_RT_BUILD_XRAY=OFF"
    "-DCOMPILER_RT_BUILD_LIBFUZZER=OFF"
    "-DCOMPILER_RT_BUILD_PROFILE=OFF"
    "-DCOMPILER_RT_BUILD_MEMPROF=OFF"
    "-DCOMPILER_RT_BUILD_ORC=OFF"
    "-DCOMPILER_RT_BUILD_GWP_ASAN=OFF"
    "-DCOMPILER_RT_DEFAULT_TARGET_ONLY=ON"
    "-DCOMPILER_RT_BAREMETAL_BUILD=ON"
    "-DCMAKE_TRY_COMPILE_TARGET_TYPE=STATIC_LIBRARY"
    "-DCOMPILER_RT_OS_DIR=baremetal"
    "-DCMAKE_C_COMPILER_TARGET=riscv32-none-elf"
    "-DCMAKE_ASM_COMPILER_TARGET=riscv32-none-elf"
  ];
}
