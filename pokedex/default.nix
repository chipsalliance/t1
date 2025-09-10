{
  lib,
  newScope,
  riscv-opcodes-src,
  ivy-rvdecoderdb,
}:
lib.makeScope newScope (scope: {
  inherit riscv-opcodes-src;

  rvopcode-cli = scope.callPackage ./rvopcode/package.nix {
    inherit ivy-rvdecoderdb;
  };

  model = scope.callPackage ./model/package.nix { };

  simulator = scope.callPackage ./simulator/package.nix { };

  softfloat = scope.callPackage ./softfloat/softfloat.nix { };
  softfloat-ext = scope.callPackage ./softfloat/package.nix { };

  tests = scope.callPackage ./tests { };
})
