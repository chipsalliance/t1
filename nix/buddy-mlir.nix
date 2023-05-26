{ stdenv
, runCommand
, lib
, cmake
, coreutils
, python3
, git
, fetchFromGitHub
, ninja
}:

let
  llvm = stdenv.mkDerivation rec {
    pname = "llvm-project";
    version = "unstable-2023-05-02";
    requiredSystemFeatures = [ "big-parallel" ];
    nativeBuildInputs = [ cmake ninja python3 ];
    src = fetchFromGitHub {
      owner = "llvm";
      repo = pname;
      rev = "8f966cedea594d9a91e585e88a80a42c04049e6c";
      hash = "sha256-g2cYk3/iyUvmIG0QCQpYmWj4L2H4znx9KbuA5TvIjrc=";
    };
    cmakeDir = "../llvm";
    cmakeFlags = [
      "-DLLVM_ENABLE_BINDINGS=OFF"
      "-DLLVM_ENABLE_OCAMLDOC=OFF"
      "-DLLVM_BUILD_EXAMPLES=OFF"
      "-DLLVM_ENABLE_PROJECTS=mlir;clang"
      "-DLLVM_TARGETS_TO_BUILD=host;RISCV"
      "-DLLVM_INSTALL_UTILS=ON"
    ];
    checkTarget = "check-mlir check-clang";
    postInstall = ''
      cp include/llvm/Config/config.h $out/include/llvm/Config
    '';
  };
  mlir_dir = runCommand "mlir_dir" { } ''
    mkdir -p $out
    ln -s ${llvm.src}/* $out
    cp -r ${llvm} $out/build
  '';

in
stdenv.mkDerivation rec {
  pname = "buddy-mlir";
  version = "unstable-2023-05-26";
  src = fetchFromGitHub {
    owner = "buddy-compiler";
    repo = pname;
    rev = "74c18e6963cf4781be254d3c5d963b36c0642ba4";
    hash = "sha256-Wx/QQrELfOT0h4B8hF9EPZKn4yVHBZeYh3Wm85Jpq60=";
  };

  ninjaFlags = [ "buddy-translate" ];

  requiredSystemFeatures = [ "big-parallel" ];

  nativeBuildInputs = [ cmake ninja ];

  passthru = { inherit llvm; };

  cmakeFlags = [
    "-DMLIR_DIR=${mlir_dir}/build/lib/cmake/mlir"
    "-DLLVM_DIR=${mlir_dir}/build/lib/cmake/llvm"
  ];
}

