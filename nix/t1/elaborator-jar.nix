{ lib
, lib'
, stdenv
, fetchMillDeps
, fetchFromGitHub
, makeWrapper
, jq
, jre

  # chisel deps
, mill
, espresso
, circt
, protobuf
, antlr4
}:

let
  submodules = {
    arithmetic = fetchFromGitHub {
      owner = "sequencer";
      repo = "arithmetic";
      rev = "1b24f89a06379231f023f6ef348218f7e6cc3023";
      hash = "sha256-fAdO7cgqcj69d14IOdZWd58HPfSY+BnrSkWnwUiB0uE=";
    };
    berkeley-hardfloat = fetchFromGitHub {
      owner = "ucb-bar";
      repo = "berkeley-hardfloat";
      rev = "d93aa570806013dea479a92ba9bb33d1f2d4f69f";
      hash = "sha256-7SqlBwWfJ5iMJDI7OGEQtKjcBgR+MBM7XM/19NOAnSY=";
    };
    cde = fetchFromGitHub {
      owner = "chipsalliance";
      repo = "cde";
      rev = "52768c97a27b254c0cc0ac9401feb55b29e18c28";
      hash = "sha256-bmiVhuriiuDFFP5gXcP2kKwdrFQ2I0Cfz3N2zed+IyY=";
    };
    chisel = fetchFromGitHub {
      owner = "chipsalliance";
      repo = "chisel";
      rev = "4c2c3d49fb309142cf68ae4b04a6bddd9a34e540";
      hash = "sha256-pMVmiaI9VskdYSUPwMvTb6EspOTuHfaW72IvS1ZzPSQ=";
    };
    rocket-chip = fetchFromGitHub {
      owner = "chipsalliance";
      repo = "rocket-chip";
      rev = "95d11684e43e5e25ae8a5f40e89f419555b103bc";
      hash = "sha256-BUONWuebqOa9VR/p+k+taOXAniyTBm1+YRI1yNV1JEs=";
    };
    rocket-chip-inclusive-cache = fetchFromGitHub {
      owner = "chipsalliance";
      repo = "rocket-chip-inclusive-cache";
      rev = "7f391c5e4cba3cdd4388efb778bd80da35d5574a";
      hash = "sha256-mr3PA/wlXkC/Cu/H5T6l1xtBrK9KQQmGOfL3TMxq5T4=";
    };
    tilelink = fetchFromGitHub {
      owner = "sequencer";
      repo = "tilelink";
      rev = "cd177e4636eb4a20326795a66e9ab502f9b2500a";
      hash = "sha256-PIPLdZSCNKHBbho0YWGODSEM8toRBlOYC2gcbh+gqIY=";
    };
  };
in

stdenv.mkDerivation rec {
  name = "t1-elaborator-jar";

  src = lib'.sourceFilesByPrefixes ./../.. [ "/v" "/build.sc" "/common.sc" "/elaborator" "/configs" ];
  sourceRoot = src.name;

  postUnpack = ''
    mkdir ${sourceRoot}/dependencies
  '' + lib.concatLines (lib.mapAttrsToList
    (k: v:
      ''
        ln -s ${v} ${sourceRoot}/dependencies/${k}
      '')
    submodules
  );

  passthru.millDeps = fetchMillDeps {
    inherit src name postUnpack;
    millDepsHash = "sha256-K7o7mC9LYLS4LDsvsGy0OXhk5sOroPdwihn4MqS8Vo4=";
  };

  nativeBuildInputs = [
    mill
    jq
    espresso
    circt
    protobuf
    antlr4
    makeWrapper
    passthru.millDeps.setupHook
  ];

  buildPhase = ''
    mill -i 'elaborator.assembly'
  '';

  installPhase = ''
    mkdir -p $out/share/java
    mv out/elaborator/assembly.dest/out.jar $out/share/java/elaborator.jar
    makeWrapper ${jre}/bin/java $out/bin/elaborator --add-flags "-jar $out/share/java/elaborator.jar"
  '';
}
