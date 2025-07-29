{
  lib,
  rv32-stdenv,
  riscv-tests,
}:
rv32-stdenv.mkDerivation {
  name = "pokedex-tests";

  src =
    with lib.fileset;
    toSource {
      root = ./.;
      fileset = unions [
        ./makefile
        ./riscv-tests-env
        ./smoke
        ./smoke_v
        ./utils
      ];
    };

  makeFlags = [
    "RISCV_PREFIX=${rv32-stdenv.targetPlatform.config}"
    "PREFIX=${placeholder "out"}"
    "RISCV_TESTS_SRC=${riscv-tests}"
  ];

  dontFixup = true;
}
