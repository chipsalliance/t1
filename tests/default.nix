{ stdenv, rv32-clang, glibc_multi, llvmForDev, go, buddy-mlir, ammonite, mill, rvv-codegen, useTarball ? false }:

let
  build-script = ../.github/scripts/ci.sc;
in stdenv.mkDerivation {
  pname = "test-case-output";
  version = "ae358e0c6000aa34bffb3b2424fd8ff499418e9";
  src = if useTarball then ./. else ./.;
  unpackPhase = ''
    # We need a writable dir for mill output
    mkdir -p tests-src
    cp -r $src/* tests-src/
  '';
  nativeBuildInputs =
    [ rv32-clang glibc_multi llvmForDev.bintools go buddy-mlir ammonite mill ];
  buildPhase = ''
    mkdir -p tests-out

    export CODEGEN_BIN_PATH=${rvv-codegen}/bin/single
    export CODEGEN_INC_PATH=${rvv-codegen}/include
    export CODEGEN_CFG_PATH=${rvv-codegen}/configs

    allTests="$(amm ${build-script} genTestBuckets --testSrcDir tests-src --bucketSize 1)"
    amm ${build-script} buildTestCases \
      --testSrcDir tests-src --outDir ./tests-out --taskBucket "$allTests"
  '';
  installPhase = ''
    mkdir -p $out/bin
    cp -r tests-out/{configs,cases} $out/bin

    # Pack up configs and tests directory for distribution
    mkdir -p $out/dist
    tar --directory tests-out --create --gzip --file tests-out.tar.gz configs cases
    cp tests-out.tar.gz $out/dist/vector-test-case.tar.gz
  '';
}
