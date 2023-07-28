{ stdenv, rv32-clang, glibc_multi, llvmForDev, go, buddy-mlir, ammonite, mill }:

let
  build-script = ../.github/scripts/ci.sc;
in
stdenv.mkDerivation {
  pname = "test-case-output";
  version = "07dc6e099ee9feebb187685b488eee176a6450fd";
  src = ./.;
  unpackPhase = ''
    mkdir -p tests-src
    cp -r $src/* tests-src/
  '';
  nativeBuildInputs =
    [ rv32-clang glibc_multi llvmForDev.bintools go buddy-mlir ammonite mill ];
  buildPhase = ''
    mkdir -p tests-out

    amm ${build-script} genTestElf tests-src ./tests-out
  '';
  installPhase = ''
    mkdir -p $out
    tar --directory $out --extract -f tests-out.tar.gz
  '';
}
