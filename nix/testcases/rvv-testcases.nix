{ stdenv

, llvmPackages
, go
, mill
, glibc_multi
, ammonite

, rv32-clang
, rvv-codegen
, buddy-mlir
, fetchMillDeps
}:
let
  pname = "rvv-testcases";
  version = "unstable-2023-09-04";
  src = ../../tests;
  buildScript = ../../.github/scripts/ci.sc;

  millDeps = fetchMillDeps {
    inherit src;
    name = "${pname}-${version}";

    millDepsHash = "sha256-ERYtxexobe8XK1RNftclghkWb0gHcfvGK72aFyywsOg=";
  };

in

stdenv.mkDerivation {
  inherit pname version src;

  nativeBuildInputs = [
    rv32-clang
    glibc_multi
    llvmPackages.bintools
    go
    buddy-mlir
    ammonite
    mill
    millDeps.setupHook
  ];

  passthru = { inherit millDeps; }; # for easier inspection

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
