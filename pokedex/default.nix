{
  lib,
  newScope,
  riscv-opcodes-src,
  ivy-rvdecoderdb,
}:
lib.makeScope newScope (scope: {
  inherit riscv-opcodes-src;

  codegen-cli = scope.callPackage ./codegen/package.nix {
    inherit ivy-rvdecoderdb;
  };

  sim-lib = scope.callPackage ./model/package.nix { };

  simulator = scope.callPackage ./simulator/package.nix { };

  test-elf = scope.callPackage ./tests/package.nix { };
})
