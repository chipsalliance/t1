{ cmake, ninja, python3, llvmPackages_17, fetchFromGitHub, fetchpatch }:
let
  stdenv = llvmPackages_17.stdenv;
  bintools = llvmPackages_17.bintools;

  buddy-llvm = fetchFromGitHub {
    owner = "llvm";
    repo = "llvm-project";
    rev = "6c59f0e1b0fb56c909ad7c9aad4bde37dc006ae0";
    hash = "sha256-bMJJ2q1hSh7m0ewclHOmIe7lOHv110rz/P7D3pw8Uiw=";
  };
in
stdenv.mkDerivation {
  pname = "buddy-mlir";
  version = "unstable-2024-07-18";

  src = fetchFromGitHub {
    owner = "buddy-compiler";
    repo = "buddy-mlir";
    rev = "ec8a17969b645f0a0c1a822ffb04192b236b5c88";
    hash = "sha256-3ecxei/nkx8sjgVkeQvZMaxr1CQXwhTz8aY1e0I3zBA=";
  };
  unpackPhase = ''
    # We can only use one-step build now...buddy-mlir have bad build system that always
    # assume the build artifacts are inside of the LLVM sources. And it also relies on
    # some LLVM Cpp source that are configured to be installed by default.
    cp -r ${buddy-llvm} llvm-project
    cp -r $src buddy-mlir

    # Directories copied from nix store are read only
    chmod -R u+w llvm-project buddy-mlir
  '';
  sourceRoot = "llvm-project";

  nativeBuildInputs = [ cmake ninja python3 bintools ];

  prePatch = "pushd ../buddy-mlir";
  patches = [
    (fetchpatch {
      url = "https://github.com/buddy-compiler/buddy-mlir/pull/357.patch";
      hash = "sha256-ysPcHAkrFJDtHmWVo35Wz6ullIGsP1EedYdJCq4fRX4=";
    })
  ];
  postPatch = "popd";

  cmakeDir = "../llvm";
  cmakeFlags = [
    "-DCMAKE_BUILD_TYPE=Release"
    "-DLLVM_INSTALL_UTILS=ON"
    "-DLLVM_ENABLE_PROJECTS=mlir"
    "-DLLVM_TARGETS_TO_BUILD=host;RISCV"
    "-DLLVM_ENABLE_ASSERTIONS=ON"
    "-DLLVM_USE_LINKER=lld"

    "-DLLVM_EXTERNAL_PROJECTS=buddy-mlir"
    "-DLLVM_EXTERNAL_BUDDY_MLIR_SOURCE_DIR=../../buddy-mlir"
  ];

  passthru.llvm = buddy-llvm;

  # No need to do check, and it also takes too much time to finish.
  doCheck = false;
}
