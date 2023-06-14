{ llvmForDev, llvmToolsForRV32Clang, fetchFromGitHub, cmake, python3, glibc_multi  }:

let
  pname = "rv-compilerrt";
  version = llvmToolsForRV32Clang.llvm.version;
  rev = llvmToolsForRV32Clang.llvm.monorepoSrc.rev;
  src = fetchFromGitHub {
    owner = "llvm";
    repo = "llvm-project";
    rev = rev;
    sha256 = "sha256-g2cYk3/iyUvmIG0QCQpYmWj4L2H4znx9KbuA5TvIjrc=";
  };
in
llvmForDev.stdenv.mkDerivation {
  sourceRoot = "${src.name}/compiler-rt";
  inherit src version pname;
  nativeBuildInputs = [ cmake python3 glibc_multi ];
  cmakeFlags = [
    "-DCOMPILER_RT_BUILD_LIBFUZZER=OFF"
    "-DCOMPILER_RT_BUILD_SANITIZERS=OFF"
    "-DCOMPILER_RT_BUILD_PROFILE=OFF"
    "-DCOMPILER_RT_BUILD_MEMPROF=OFF"
    "-DCOMPILER_RT_BUILD_ORC=OFF"
    "-DCOMPILER_RT_BUILD_BUILTINS=ON"
    "-DCOMPILER_RT_BAREMETAL_BUILD=ON"
    "-DCOMPILER_RT_INCLUDE_TESTS=OFF"
    "-DCOMPILER_RT_HAS_FPIC_FLAG=OFF"
    "-DCOMPILER_RT_DEFAULT_TARGET_ONLY=On"
    "-DCOMPILER_RT_OS_DIR=riscv32"
    "-DCMAKE_BUILD_TYPE=Release"
    "-DCMAKE_SYSTEM_NAME=Generic"
    "-DCMAKE_SYSTEM_PROCESSOR=riscv32"
    "-DCMAKE_TRY_COMPILE_TARGET_TYPE=STATIC_LIBRARY"
    "-DCMAKE_SIZEOF_VOID_P=8"
    "-DCMAKE_ASM_COMPILER_TARGET=riscv32-none-elf"
    "-DCMAKE_C_COMPILER_TARGET=riscv32-none-elf"
    "-DCMAKE_C_COMPILER_WORKS=ON"
    "-DCMAKE_CXX_COMPILER_WORKS=ON"
    "-DCMAKE_C_COMPILER=clang"
    "-DCMAKE_CXX_COMPILER=clang++"
    "-Wno-dev"
  ];
  CMAKE_C_FLAGS = "-nodefaultlibs -fno-exceptions -mno-relax -Wno-macro-redefined -fPIC";
}
