{ stdenv
, lib'

, llvmPackages
, go
, mill
, glibc_multi
, ammonite

, rv32-clang
, rvv-codegen
, buddy-mlir
, fetchMillDeps
, rv32-gnu-toolchain
}:

stdenv.mkDerivation rec {
  name = "rvv-testcases";
  src = ../../../tests;

  nativeBuildInputs = [
    rv32-clang
    glibc_multi
    llvmPackages.bintools
    go
    buddy-mlir
    ammonite
    mill
    rv32-gnu-toolchain
    passthru.millDeps.setupHook
  ];

  passthru.millDeps = fetchMillDeps {
    src = lib'.sourceFilesByPrefixes src [ "/build.sc" "/configs" ];
    inherit name;

    millDepsHash = "sha256-ERYtxexobe8XK1RNftclghkWb0gHcfvGK72aFyywsOg=";
  };

  env = {
    CODEGEN_BIN_PATH = "${rvv-codegen}/bin/single";
    CODEGEN_INC_PATH = "${rvv-codegen}/include";
    CODEGEN_CFG_PATH = "${rvv-codegen}/configs";
  };

  buildPhase = ''
    runHook preBuild
    mkdir -p $out
    patchShebangs ./buildAll.sc
    ./buildAll.sc . $out
    runHook postBuild
  '';

  dontFixup = true;
}
