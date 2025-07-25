{ rv32-stdenv, riscv-tests }:
rv32-stdenv.mkDerivation {
  name = "pokedex-tests";

  src = ./.;

  makeFlags = [
    "RISCV_PREFIX=${rv32-stdenv.targetPlatform.config}"
    "PREFIX=${placeholder "out"}"
    "RISCV_TESTS_SRC=${riscv-tests}"
  ];

  dontFixup = true;
}
