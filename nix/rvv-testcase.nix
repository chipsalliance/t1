{ stdenv, rv32-clang, glibc_multi, llvmForDev, go, buddy-mlir, ammonite, mill, rvv-codegen, fetchFromGitHub }:
stdenv.mkDerivation {
  pname = "rvv-testcase";
  version = "unstable-2023-07-31";
  src = fetchFromGitHub {
    owner = "sequencer";
    repo = "vector";
    # Remember to replace this to master branch after testplan branch got merged
    rev = "92819bb91d4b867d1727c737f079a5af2b6863a6";
    sparseCheckout = [ "tests" ".github/scripts" ];
    sha256 = "sha256-Hi2JoE+uA9H/jVhEGk3fC76Oe/pIemZmk4bjWs7B3so=";
  };
  unpackPhase = ''
    # mill will write data into the working directory, so the workdir cannot be $src as it is not writable
    mkdir -p tests-src
    cp -r $src/tests/* tests-src/
    cp $src/.github/scripts/ci.sc .
  '';
  nativeBuildInputs =
    [ rv32-clang glibc_multi llvmForDev.bintools go buddy-mlir ammonite mill ];
  buildPhase = ''
    mkdir -p tests-out

    export CODEGEN_BIN_PATH=${rvv-codegen}/bin/single
    export CODEGEN_INC_PATH=${rvv-codegen}/include
    export CODEGEN_CFG_PATH=${rvv-codegen}/configs

    amm ci.sc buildAllTestCase ./tests-src ./tests-out
  '';
  installPhase = ''
    mkdir -p $out
    cp -r tests-out/{configs,cases} $out
  '';
}
