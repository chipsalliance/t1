{ fetchFromGitHub, buildGoModule }:
let
  riscv-test-env = fetchFromGitHub {
    owner = "riscv";
    repo = "riscv-test-env";
    rev = "1c577dc7c7d6aee27b8d5cb0e2e87c8473e3ad12";
    hash = "sha256-JZ7Yn4cTel9uVo6uGu0zs3IMMySoGzjSMW544YLYips=";
  };
in
buildGoModule {
  pname = "riscv-vector-test";
  version = "unstable-2023-04-12";
  src = fetchFromGitHub {
    owner = "ksco";
    repo = "riscv-vector-tests";
    rev = "4dc7375d8992fbe44dffc5a6bdff2361d9290808";
    hash = "sha256-+S2JRUOOsDEv1nQ3PvTSego0NEKyOTNTyls/jMIauX0=";
  };
  doCheck = false;
  vendorHash = "sha256-9cQlivpHg6IDYpmgBp34n6BR/I0FIYnmrXCuiGmAhNE=";
  # Get rid of copying the whole source
  postInstall = ''
    cp -r $src/configs $out/configs
    mkdir $out/include
    cp $src/macros/sequencer-vector/* $out/include
    cp $src/env/sequencer-vector/* $out/include

    cp ${riscv-test-env}/encoding.h $out/include
    # Let riscv_test.h find the encoding.h file correctly
    sed -i 's/^#include "..\/encoding.h"$/#include "encoding.h"/' $out/include/riscv_test.h
  '';
}

