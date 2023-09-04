{ cmake, ninja, python3, llvmPackages_16, fetchFromGitHub, fetchpatch }:
# Use clang instead of gcc to build
llvmPackages_16.stdenv.mkDerivation {
  pname = "buddy-mlir";
  version = "unstable-2023-08-28";

  srcs = [
    # Using git submodule to obtain the llvm source is really slow.
    # So here I use GitHub archive to download the sources.
    (fetchFromGitHub {
      owner = "llvm";
      repo = "llvm-project";
      rev = "8f966cedea594d9a91e585e88a80a42c04049e6c";
      sha256 = "sha256-g2cYk3/iyUvmIG0QCQpYmWj4L2H4znx9KbuA5TvIjrc=";
    })
    (fetchFromGitHub {
        owner = "buddy-compiler";
        repo = "buddy-mlir";
        rev = "7b420a7c23604de2153b919132a7909f30b2cefb";
        sha256 = "sha256-m1eabnPo2XXaw7MREt8j9ORQXUvVuWawpJrt2mi/wzM=";
    })
  ];
  sourceRoot = "llvm-project";
  unpackPhase = ''
    sourceArray=($srcs)
    cp -r ''${sourceArray[0]} llvm-project
    cp -r ''${sourceArray[1]} buddy-mlir

    # Directories copied from nix store are read only
    chmod -R u+w llvm-project buddy-mlir
  '';

  prePatch = "pushd $NIX_BUILD_TOP/buddy-mlir";
  patches = [
    (fetchpatch {
       url = "https://github.com/buddy-compiler/buddy-mlir/pull/193.patch";
       sha256 = "sha256-scj3jtxrUGLBpHsQRoYYJ8ijqJ22pRu9RU2k7EPe95A=";
     })
  ];
  postPatch = "popd";

  nativeBuildInputs = [ cmake ninja python3 llvmPackages_16.bintools ];

  cmakeDir = "../llvm";
  cmakeFlags = [
    "-DCMAKE_BUILD_TYPE=Release"
    "-DLLVM_ENABLE_PROJECTS=mlir"
    "-DLLVM_TARGETS_TO_BUILD=host;RISCV"
    "-DLLVM_ENABLE_ASSERTIONS=ON"
    "-DLLVM_USE_LINKER=lld"

    "-DLLVM_EXTERNAL_PROJECTS=buddy-mlir"
    "-DLLVM_EXTERNAL_BUDDY_MLIR_SOURCE_DIR=../../buddy-mlir"
  ];

  checkTarget = "check-mlir check-buddy";
}
