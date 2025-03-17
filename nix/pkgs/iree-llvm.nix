{ cmake
, ninja
, llvmPackages
}:
let
  stdenv = llvmPackages.stdenv;
in
stdenv.mkDerivation {
  name = "llvm-for-iree";
  version = "8cb4b3e21e03d3e029ade27139eab1a25720c773";
  src = fetchFromGitHub {
    owner = "llvm";
    repo = "llvm-project";
    rev = version;
    hash = "";
  };

  buildInputs = [
    cmake
    ninja
  ]

    cmakeDir = "../llvm";
  cmakeFlags = [
    # These defaults are moderately important to us, but the user *can*
    # override them (enabling some of these brings in deps that will conflict,
    # so ymmv).
    "-DLLVM_INCLUDE_EXAMPLES=OFF"
    "-DLLVM_INCLUDE_TESTS=OFF"
    "-DLLVM_INCLUDE_BENCHMARKS=OFF"
    "-DLLVM_APPEND_VC_REV=OFF"
    "-DLLVM_ENABLE_IDE=ON"
    "-DLLVM_ENABLE_BINDINGS=OFF"

    # Force LLVM to avoid dependencies, which we don't ever really want in our  
    # limited builds.
    "-DLLVM_ENABLE_LIBEDIT=OFF"
    "-DLLVM_ENABLE_LIBXML2=OFF"
    "-DLLVM_ENABLE_TERMINFO=OFF"
    "-DLLVM_ENABLE_ZLIB=OFF"
    "-DLLVM_ENABLE_ZSTD=OFF"
    "-DLLVM_FORCE_ENABLE_STATS=ON"

    # Default Python bindings to off (for all sub-projects).
    "-DMLIR_ENABLE_BINDINGS_PYTHON=OFF"
    "-DMHLO_ENABLE_BINDINGS_PYTHON=OFF"

    # Disable MLIR attempting to configure Python dev packages. We take care of
    # that in IREE as a super-project.
    "-DMLIR_DISABLE_CONFIGURE_PYTHON_DEV_PACKAGES=ON"

    "-DLLVM_ENABLE_PROJECTS=mlir;clang;lld"
    "-DLLVM_TARGETS_TO_BUILD=X86;ARM;AArch64;RISCV;WebAssembly;AMDGPU"

    # Disable LLVM's warnings.
    "-DLLVM_ENABLE_WARNINGS=OFF"


    "-DLLVM_ENABLE_PROJECTS=mlir"
    "-DLLVM_TARGETS_TO_BUILD=host;RISCV"
    "-DLLVM_ENABLE_ASSERTIONS=ON"
    "-DCMAKE_BUILD_TYPE=Release"
    # required for MLIR python binding
    "-DMLIR_ENABLE_BINDINGS_PYTHON=ON"
    # required for not, FileCheck...
    "-DLLVM_INSTALL_UTILS=ON"
  ];
}
