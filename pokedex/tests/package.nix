{ rv32-stdenv }:
rv32-stdenv.mkDerivation {
  name = "pokedex-tests";

  src = ./.;

  makeFlags = [
    "RISCV_PREFIX=${rv32-stdenv.targetPlatform.config}"
    "PREFIX=${placeholder "out"}"
  ];

  dontFixup = true;
}
