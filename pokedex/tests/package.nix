{
  lib,
  rv32-stdenv,
  riscv-tests,
  riscv-vector-tests,
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
        ./riscv-vector-tests
        ./smoke
        ./smoke_v
        ./utils
      ];
    };

  makeFlags = [
    # TODO: configurable
    "VLEN=256"
    "RISCV_PREFIX=${rv32-stdenv.targetPlatform.config}"
    "PREFIX=${placeholder "out"}"
    "RISCV_TESTS_SRC=${riscv-tests}"
    "CODEGEN_INSTALL_DIR=${riscv-vector-tests}"
  ];

  dontFixup = true;
}
