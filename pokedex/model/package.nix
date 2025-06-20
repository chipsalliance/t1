{
  lib,
  stdenv,
  codegen-cli,
  riscv-opcodes-src,
  asl-interpreter,
}:
stdenv.mkDerivation {
  name = "pokedex-simlib";
  src = lib.cleanSource ./.;

  nativeBuildInputs = [
    codegen-cli
    asl-interpreter
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
