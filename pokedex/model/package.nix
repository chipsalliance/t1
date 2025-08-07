{
  lib,
  stdenv,
  rvopcode-cli,
  riscv-opcodes-src,
  asl-interpreter,
  minijinja,
}:
stdenv.mkDerivation {
  name = "pokedex-model";
  src = lib.cleanSource ./.;

  nativeBuildInputs = [
    rvopcode-cli
    asl-interpreter
    minijinja
  ];

  env = {
    # This is not necessary, just help manually invoke makefile easier
    RISCV_OPCODES_SRC = "${riscv-opcodes-src}";
  };

  makeFlags = [
    "PREFIX=${placeholder "out"}"
    "RISCV_OPCODES_SRC=${riscv-opcodes-src}"
  ];
}
