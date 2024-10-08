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

      accelerate = (prev.accelerate.overridePythonAttrs (old: rec {
        pname = "accelerate";
        version = "0.32.0";

        src = fetchFromGitHub {
          owner = "huggingface";
          repo = "accelerate";
          rev = "refs/tags/v${version}";
          hash = "sha256-/Is5aKTYHxvgUJSkF7HxMbEA6dgn/y5F1B3D6qSCSaE=";
        };
      }));

      torch = (prev.torch.overridePythonAttrs (old: rec {
        version = "2.3.1";
        src = fetchFromGitHub {
          owner = "pytorch";
          repo = "pytorch";
          rev = "refs/tags/v${version}";
          fetchSubmodules = true;
          hash = "sha256-vpgtOqzIDKgRuqdT8lB/g6j+oMIH1RPxdbjtlzZFjV8=";
        };
        PYTORCH_BUILD_VERSION = version;
        PYTORCH_BUILD_NUMBER = 0;
      }));

      torchvision = prev.torchvision.overridePythonAttrs rec {
        version = "0.18.1";

        src = fetchFromGitHub {
          owner = "pytorch";
          repo = "vision";
          rev = "refs/tags/v${version}";
          hash = "sha256-aFm6CyoMA8HtpOAVF5Q35n3JRaOXYswWEqfooORUKsw=";
        };
      };
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
