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
  riscv-tests = scope.callPackage (
    { fetchFromGitHub }:
    fetchFromGitHub {
      repo = "riscv-tests";
      owner = "riscv-software-src";
      rev = "b5ba87097c42aa41c56657e0ae049c2996e8d8d8";
      hash = "sha256-VaaGzlEsOSjtUhDewxoM77wJUW9Yu646CYefWeggLys=";
      postFetch = ''
        rm -v $out/isa/rv32ui/ma_data.S
      '';
    }
  ) { };
})
