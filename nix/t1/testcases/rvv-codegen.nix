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
    rev = "5991f7fa15be808a1fcdfd80f56d30c7dc243177";
    hash = "sha256-3MNKU9CVBfpJa6TkrZCrS+yn0BYdt2zu0Cv0Wf+3wpk=";
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

