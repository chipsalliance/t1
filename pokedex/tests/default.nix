{ lib, newScope }:
lib.makeScope newScope (scope: {
  all-tests = scope.callPackage ./package.nix { };

  global-pokedex-config = scope.callPackage (
    { writeText }:
    writeText "dts.kdl" ''
      sram "naive" base=0x80000000 length=0x20000000

      mmio base=0x40000000 length=0x1000 {
        mmap "exit" offset=0x4
      }
    ''
  ) { };
  pokedex-log = scope.callPackage ./pokedex-log.nix { };
  difftest-meta = scope.callPackage ./difftest-meta.nix { };
})
