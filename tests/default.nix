{ stdenv, rv32-clang, glibc_multi, llvmForDev, go, buddy-mlir, ammonite, mill, fetchFromGitHub, buildGoModule }:

let
  build-script = ../.github/scripts/ci.sc;

  # We need the codegen/config, so we need to store the source code
  riscv-vector-tests = fetchFromGitHub {
    owner = "ksco";
    repo = "riscv-vector-tests";
    rev = "3fb992b1dc7f89231b27ae4a1e8d50492dde4f5b";
    hash = "sha256-BNbK8+KUwhqk3XfFgKsaeUpX0NuApl8mN/URKwYTYtE=";
  };

  codegen = buildGoModule {
    pname = "riscv-vector-test";
    version = "unstable-2023-04-12";
    src = riscv-vector-tests;
    doCheck = false;
    vendorHash = "sha256-9cQlivpHg6IDYpmgBp34n6BR/I0FIYnmrXCuiGmAhNE=";
  };
in
stdenv.mkDerivation {
  pname = "test-case-output";
  version = "ae358e0c6000aa34bffb3b2424fd8ff499418e9";
  src = ./.;
  unpackPhase = ''
    mkdir -p tests-src
    cp -r $src/* tests-src/
    cp -r ${riscv-vector-tests} tests-src/codegen
  '';
  nativeBuildInputs =
    [ rv32-clang glibc_multi llvmForDev.bintools go buddy-mlir ammonite mill ];
  buildPhase = ''
    mkdir -p tests-out

    export CODEGEN_BIN_PATH=${codegen}/bin/single
    amm ${build-script} genTestElf tests-src ./tests-out
  '';
  installPhase = ''
    mkdir -p $out
    tar --directory $out --extract -f tests-out.tar.gz
  '';
}
