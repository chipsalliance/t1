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
    rev = "f8346b6d963572c259c4fb36cb61caab570256ad";
    hash = "sha256-H+hkKWqkr1lQQqSIGuccfQk5JvFAuMyI4oFgbv4g9YU=";
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

