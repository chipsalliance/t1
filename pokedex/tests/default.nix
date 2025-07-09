{ lib, newScope }:
lib.makeScope newScope (scope: {
  test-elf = scope.callPackage ./package.nix { };

  pokedex-log = scope.callPackage ./pokedex-log.nix { };
  difftest-meta = scope.callPackage ./difftest-meta.nix { };
})
