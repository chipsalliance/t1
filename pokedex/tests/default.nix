{ lib, newScope }:
lib.makeScope newScope (scope: {
  global-pokedex-config = ./pokedex-config.kdl;

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

  prebuilt-cases = scope.callPackage ./nix/prebuilt-cases.nix { };

  run = scope.callPackage ./nix/run.nix { };

  env = scope.callPackage (
    { mkShell, rv32-stdenv }:
    mkShell {
      packages = [ rv32-stdenv.cc ];
      inputsFrom = [
        scope.prebuilt-cases
        scope.run
      ];
      env = scope.run.env // {
        # Using mesonFlags accidentally overlap the default key.
        MESON_FLAGS =
          (
            scope.prebuilt-cases.mesonFlags
            ++ [
              (lib.mesonEnable "with_tests" true)
            ]
          )
          |> toString;
      };
      shellHook = ''
        cat <<EOF
        Three commands to do all the jobs
        1. Configure: meson setup build \$MESON_FLAGS --prefix \$PWD/tests-output
        2. Test: meson test -C build
        EOF
      '';
    }
  ) { };
})
