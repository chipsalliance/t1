{ rv32-clang, glibc_multi, llvmForDev, go, buddy-mlir, ammonite, mill, rvv-codegen, fetchMillDeps, millSetupHook }:
let
  pname = "rvv-testcase";
  version = "unstable-2023-09-04";
  src = ../tests;
  buildScript = ../.github/scripts/ci.sc;

  millDeps = fetchMillDeps {
    inherit pname src;

    millDepsHash = "sha256-onyUHOYJ7nuN7aSgaVEmgLsdjB6qR9n09MonLe79bEE=";
  };
in
llvmForDev.stdenv.mkDerivation {
  inherit pname version src millDeps;

  nativeBuildInputs = [
    rv32-clang
    glibc_multi
    llvmForDev.bintools
    go
    buddy-mlir
    ammonite
    mill
    millSetupHook
  ];

  buildPhase = ''
    mkdir -p tests-out

    export CODEGEN_BIN_PATH=${rvv-codegen}/bin/single
    export CODEGEN_INC_PATH=${rvv-codegen}/include
    export CODEGEN_CFG_PATH=${rvv-codegen}/configs

    amm ${buildScript} buildAllTestCase . ./tests-out
  '';

  installPhase = ''
    mkdir -p $out
    cp -r tests-out/{configs,cases} $out
  '';

  dontPatchShebangs = true;
  dontPatchELF = true;
  dontStrip = true;
  dontMoveSbin = true;
  dontFixup = true;
}
