{ lib, newScope }:
lib.makeScope newScope (scope: {
  test-elf = scope.callPackage ./package.nix { };

  run-test-elf = scope.callPackage ./run-test-elf.nix { };
})
