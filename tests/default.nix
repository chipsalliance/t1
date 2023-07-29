{ stdenv, rv32-clang, glibc_multi, llvmForDev, go, buddy-mlir, ammonite, mill
, fetchFromGitHub, buildGoModule }:

let
  build-script = ../.github/scripts/ci.sc;

  codegen = buildGoModule {
    pname = "riscv-vector-test";
    version = "unstable-2023-04-12";
    src = fetchFromGitHub {
      owner = "ksco";
      repo = "riscv-vector-tests";
      rev = "3fb992b1dc7f89231b27ae4a1e8d50492dde4f5b";
      hash = "sha256-BNbK8+KUwhqk3XfFgKsaeUpX0NuApl8mN/URKwYTYtE=";
    };
    doCheck = false;
    vendorHash = "sha256-9cQlivpHg6IDYpmgBp34n6BR/I0FIYnmrXCuiGmAhNE=";
    # Get rid of copying the whole source
    postInstall = ''
      cp -r $src/configs $out/configs
      mkdir $out/include
      cp $src/macros/sequencer-vector/* $out/include
      cp $src/env/sequencer-vector/* $out/include

      cp $src/env/encoding.h $out/include
      # Let riscv_test.h find the encoding.h file correctly
      sed -i 's/^#include "..\/encoding.h"$/#include "encoding.h"/' $out/include/riscv_test.h
    '';
  };
in stdenv.mkDerivation {
  pname = "test-case-output";
  version = "ae358e0c6000aa34bffb3b2424fd8ff499418e9";
  src = ./.;
  unpackPhase = ''
    mkdir -p tests-src
    cp -r $src/* tests-src/
    mkdir -p tests-src/codegen
    cp -r ${codegen}/configs tests-src/codegen/
  '';
  nativeBuildInputs =
    [ rv32-clang glibc_multi llvmForDev.bintools go buddy-mlir ammonite mill ];
  buildPhase = ''
    mkdir -p tests-out

    export CODEGEN_BIN_PATH=${codegen}/bin/single
    export CODEGEN_INC_PATH=${codegen}/include
    amm ${build-script} genTestElf tests-src ./tests-out
  '';
  installPhase = ''
    mkdir -p $out
    tar --directory $out --extract -f tests-out.tar.gz
  '';
}
