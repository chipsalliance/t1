{ cmake
, ninja
, llvmPackages_17
, fetchFromGitHub
, fetchpatch
, python3
, callPackage
}:
let
  stdenv = llvmPackages_17.stdenv;
  bintools = llvmPackages_17.bintools;

  buddy-llvm = callPackage ./buddy-llvm.nix { inherit stdenv python3; };
  self = stdenv.mkDerivation {
    pname = "buddy-mlir";
    version = "unstable-2024-07-18";

    src = fetchFromGitHub {
      owner = "buddy-compiler";
      repo = "buddy-mlir";
      rev = "802cefe91199c0935122546d463e400bee8635a6";
      hash = "sha256-d8e/VM5LrsEwsC7NyNy/kdBp0fpY/CWeItrk4adOK0A=";
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

    # Here we concatenate the LLVM and Buddy python module into one directory for easier import
    postFixup = ''
      mkdir -p $out/lib/python${python3.pythonVersion}/site-packages
      cp -vr $out/python_packages/buddy $out/lib/python${python3.pythonVersion}/site-packages/
      cp -vr ${buddy-llvm}/python_packages/mlir_core/mlir $out/lib/python${python3.pythonVersion}/site-packages/
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
        ps.torchvision
      ]);
    };
  };
in
self
