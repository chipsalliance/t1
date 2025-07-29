{ lib, newScope }:
lib.makeScope newScope (scope: {
  all-tests = scope.callPackage ./package.nix { };

  global-pokedex-config = ./pokedex-config.kdl;

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
