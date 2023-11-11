{
  description = "vector";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }@inputs:
    let
      overlay = import ./nix/overlay.nix;
    in
    flake-utils.lib.eachDefaultSystem
      (system:
        let
          pkgs = import nixpkgs { inherit system; overlays = [ overlay ]; };
          commonDeps = with pkgs; [
            gnused
            coreutils
            gnumake
            gnugrep
            which
            parallel
          ];

          chiselDeps = with pkgs; [
            mill
            espresso
            circt
            protobuf
            antlr4
          ];

          emulatorDeps = with pkgs; [
            cmake
            libargs
            spdlog
            fmt
            libspike
            nlohmann_json
            ninja

            # for verilator
            verilator
            zlib

            # for CI
            ammonite

            # wave interpreter
            wal-lang
          ];

          mkLLVMShell = pkgs.mkShell.override { stdenv = pkgs.llvmForDev.stdenv; };
          postHook = ''
            # workaround for https://github.com/NixOS/nixpkgs/issues/214945
            export PATH="${pkgs.clang-tools}/bin:$PATH"
          '';
        in
        {
          legacyPackages = pkgs;
          devShells = {
            default = mkLLVMShell {
              buildInputs = commonDeps ++ chiselDeps ++ emulatorDeps;
              env.TEST_CASES_DIR = "${pkgs.t1.rvv-testcases}";
              inherit postHook;
            };
          };

          formatter = pkgs.nixpkgs-fmt;
        }
      )
    // { inherit inputs; overlays.default = overlay; };
}
