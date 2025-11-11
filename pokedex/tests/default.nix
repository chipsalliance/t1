{ lib, newScope }:
lib.makeScope newScope (scope: {
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

  # Group entry, linker script and riscv-test-env header together for easier usage
  pokedex-compile-stubs = ./compile-stubs;

  smoke-tests = scope.callPackage ./smoke/package.nix { };

  smoke-v-tests = scope.callPackage ./smoke_v/package.nix { };

  riscv-tests-bins = scope.callPackage ./riscv-tests/package.nix { };

  riscv-vector-tests-bins = scope.callPackage ./riscv-vector-tests/package.nix { };

  batch-run-difftest = scope.callPackage ./batch-difftest.nix { };
})
