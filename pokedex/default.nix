{
  lib,
  newScope,
  riscv-opcodes-src,
}:
lib.makeScope newScope (scope: {
  inherit riscv-opcodes-src;

  model = scope.callPackage ./model/package.nix { };

  simulator = scope.callPackage ./simulator/package.nix { };

  tests = scope.callPackage ./tests { };
})
