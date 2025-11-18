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

  # Builder for running difftest for one test case
  mkDiffEnv = scope.callPackage ./make-diff-env.nix { };

  # Handwritten ASM for driving the simulator
  # Developer can run `nix build/develop .#pokedex.tests.smoke-tests.diff.<attr>` to test single test binary.
  smoke-tests = scope.callPackage ./smoke/package.nix { };

  # Handwritten ASM for driving the simulator with Vector extension
  # Developer can run `nix build/develop .#pokedex.tests.smoke-v-tests.diff.<attr>` to test single test binary.
  smoke-v-tests = scope.callPackage ./smoke_v/package.nix { };

  # Binaries compiled from riscv-software-src/riscv-tests
  # Developer can run `nix build/develop .#pokedex.tests.riscv-tests-bins.diff.<attr>` to test single test binary.
  riscv-tests-bins = scope.callPackage ./riscv-tests/package.nix { };

  # Binaries compiled from chipsalliance/riscv-vector-tests
  # Developer can run `nix build/develop .#pokedex.tests.riscv-vector-tests-bins.diff.<attr>` to test single test binary.
  riscv-vector-tests-bins = scope.callPackage ./riscv-vector-tests/package.nix { };

  # An internal helper derivation that run difftest on all the binaries
  # provided by smoke-tests, smoke-v-tests, riscv-tests-bins,
  # riscv-vector-tests-bins
  all-diffs = scope.callPackage ./all-diffs.nix { };
})
