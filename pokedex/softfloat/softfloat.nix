{ stdenv, fetchFromGitHub }:
stdenv.mkDerivation {
  name = "softfloat-for-pokedex";

  src = fetchFromGitHub {
    owner = "ucb-bar";
    repo = "berkeley-softfloat-3";
    rev = "a0c6494cdc11865811dec815d5c0049fba9d82a8";
    hash = "sha256-TO1DhvUMd2iP5gvY9Hqy9Oas0Da7lD0oRVPBlfAzc90=";
  };

  makeFlags = [
    # TODO: replace this with configurable map if we want to support running pokedex simulator on AArch64
    "--directory=build/Linux-x86_64-GCC"
    "SPECIALIZE_TYPE=RISCV"
  ];

  installPhase = ''
    runHook preInstall

    mkdir -p "$out/lib"
    cp build/Linux-x86_64-GCC/softfloat.a "$out/lib/libsoftfloat.a"
    cp -r source/include "$out/"

    runHook postInstall
  '';
}
