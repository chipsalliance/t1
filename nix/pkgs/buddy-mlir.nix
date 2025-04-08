{
  lib,
  cmake,
  ninja,
  llvmPackages_17,
  fetchFromGitHub,
  fetchurl,
  python3,
  callPackage,
}:
let
  stdenv = llvmPackages_17.stdenv;
  bintools = llvmPackages_17.bintools;

  buddy-llvm = callPackage ./buddy-llvm.nix { inherit stdenv python3; };
  self = stdenv.mkDerivation {
    pname = "buddy-mlir";
    version = "unstable-2024-07-18";

    src = fetchFromGitHub {
      owner = "WuXintong123";
      repo = "buddy-mlir";
      rev = "6586555adf921371906fe908293714bff4d92b24";
      hash = "sha256-NDdj72oNhIKcU7cOw+RDzPrjKLIUVY63TDUrJ2DzYL0=";
    };

    postPatch = ''
      sed -i \
        's|link_directories(''${LLVM_BINARY_DIR}/tools/mlir/|link_directories(''${LLVM_BINARY_DIR}/|' \
        midend/python/CMakeLists.txt
    '';

    nativeBuildInputs = [
      cmake
      ninja
      bintools
    ];
    buildInputs = [
      buddy-llvm
    ];

    cmakeFlags = [
      "-DMLIR_DIR=${buddy-llvm}/lib/cmake/mlir"
      "-DLLVM_DIR=${buddy-llvm}/lib/cmake/llvm"
      "-DLLVM_MAIN_SRC_DIR=${buddy-llvm.src}/llvm"
      "-DBUDDY_MLIR_ENABLE_PYTHON_PACKAGES=ON"
      "-DCMAKE_BUILD_TYPE=Release"
    ];

    # No need to do check, and it also takes too much time to finish.
    doCheck = false;

    # TODO: Upstream this to Buddy-MLIR cmake install
    postInstall = ''
      mkdir -p "$out/include"
      cp -vr "$NIX_BUILD_TOP/$sourceRoot/frontend/Interfaces/buddy" "$out/include"
    '';

    # Here we concatenate the LLVM and Buddy python module into one directory for easier import
    postFixup = ''
      mkdir -p $out/lib/python${python3.pythonVersion}/site-packages
      cp -vr $out/python_packages/buddy $out/lib/python${python3.pythonVersion}/site-packages/
      cp -vr ${buddy-llvm.lib}/python_packages/mlir_core/mlir $out/lib/python${python3.pythonVersion}/site-packages/
    '';

    passthru = {
      llvm = buddy-llvm;

      # Below three fields are black magic that allow site-packages automatically imported with nixpkgs hooks
      pythonModule = python3;
      pythonPath = [ ];
      requiredPythonModules = [ ];

      # nix run buddy-mlir.pyenv to start a python with PyTorch/LLVM MLIR/Buddy Frontend support
      pyenv = python3.withPackages (ps: [
        self
        ps.torch

        # mobilenet
        ps.torchvision

        # tinyllama
        ps.transformers
        ps.accelerate
        ps.sentencepiece
      ]);
    };
  };
in
self
