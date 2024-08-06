{ cmake
, ninja
, llvmPackages_17
, fetchFromGitHub
, fetchpatch
, callPackage
}:
let
  stdenv = llvmPackages_17.stdenv;
  bintools = llvmPackages_17.bintools;

  buddy-llvm = callPackage ./buddy-llvm.nix { inherit stdenv; };
in
stdenv.mkDerivation {
  pname = "buddy-mlir";
  version = "unstable-2024-07-18";

  src = fetchFromGitHub {
    owner = "buddy-compiler";
    repo = "buddy-mlir";
    rev = "d7d90a488ac0d6fc1e700e932f842c7b2bcad816";
    hash = "sha256-MhykCa6Z7Z8PpAlNh+vMuWYEOZZDyWhtMzMnFlNbGIk=";
  };

  nativeBuildInputs = [ cmake ninja bintools ];
  buildInputs = [
    buddy-llvm
  ];

  cmakeFlags = [
    "-DMLIR_DIR=${buddy-llvm.dev}/lib/cmake/mlir"
    "-DLLVM_DIR=${buddy-llvm.dev}/lib/cmake/llvm"
    "-DLLVM_MAIN_SRC_DIR=${buddy-llvm.src}/llvm"
    "-DBUDDY_MLIR_ENABLE_PYTHON_PACKAGES=ON"
    "-DCMAKE_BUILD_TYPE=Release"
  ];

  # No need to do check, and it also takes too much time to finish.
  doCheck = false;
}
