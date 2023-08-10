{ stdenv, rv32-clang, glibc_multi, llvmForDev, go, buddy-mlir, ammonite, mill, rvv-codegen }:
stdenv.mkDerivation {
  pname = "rvv-testcase";
  version = "unstable-2023-07-31";
  srcs = [
    ../.github/scripts/ci.sc
    ../tests
  ];
  sourceRoot = ".";
  unpackPhase = ''
    # mill will write data into the working directory, so the workdir cannot be $src as it is not writable
    mkdir -p tests-src
    for src in $srcs; do
      case $src in
        *-ci.sc)
          cp $src ./ci.sc
          ;;
        *-tests)
          cp -r $src/* tests-src
          ;;
      esac
    done
  '';
  nativeBuildInputs =
    [ rv32-clang glibc_multi llvmForDev.bintools go buddy-mlir ammonite mill ];
  buildPhase = ''
    mkdir -p tests-out

    export CODEGEN_BIN_PATH=${rvv-codegen}/bin/single
    export CODEGEN_INC_PATH=${rvv-codegen}/include
    export CODEGEN_CFG_PATH=${rvv-codegen}/configs

    # Ammonite will write some Jar file in $HOME directory,
    # however nix will set a non-existent directory as home directory 
    # which will cause Ammonite fail to write and read.
    mkdir fake-home
    export HOME=$PWD/fake-home

    amm ci.sc buildAllTestCase ./tests-src ./tests-out
  '';
  installPhase = ''
    mkdir -p $out
    cp -r tests-out/{configs,cases} $out
  '';
  __noChroot = true;
}
