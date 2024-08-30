{ cmake
, ninja
, llvmPackages_17
, fetchFromGitHub
, fetchurl
, python3
, callPackage
}:
let
  stdenv = llvmPackages_17.stdenv;
  bintools = llvmPackages_17.bintools;

  downgradedPyPkgs = python3.override {
    packageOverrides = final: prev: {
      tokenizers = (final.callPackage ./tokenizer-013.nix { });

      transformers = (prev.transformers.overridePythonAttrs (old: rec {
        version = "4.33.1";

        src = fetchFromGitHub {
          owner = "huggingface";
          repo = "transformers";
          rev = "refs/tags/v${version}";
          hash = "sha256-Z78I9S8g9WexoX6HhxwbOD0K0awCTzsqW1ZiWObQNw0=";
        };
      }));
    };
  };

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
      pythonModule = downgradedPyPkgs;
      pythonPath = [ ];
      requiredPythonModules = [ ];

      # nix run buddy-mlir.pyenv to start a python with PyTorch/LLVM MLIR/Buddy Frontend support
      pyenv = downgradedPyPkgs.withPackages (ps: [
        self
        ps.torch

        # mobilenet
        ps.torchvision

        # tinyllama
        ps.transformers
        ps.accelerate
      ]);
    };
  };
in
self
