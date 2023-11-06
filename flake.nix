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
            # clangd provided in llvmPackages_14 doesn't handle nix rpath, while the one in clang-tools package does.
            # However, since we are using the stdenv from llvmPackages_14, the bin path clang-tools always comes after
            # the llvmPackages_14. Thus we need a workaround to make sure that we can have `clangd` binary points to
            # the one provided by clang-tools package
            export PATH="${pkgs.clang-tools}/bin:$PATH"
          '';
        in
        {
          legacyPackages = pkgs;
          devShells = {
            # for chisel-only development
            chisel = pkgs.mkShell {
              buildInputs = commonDeps ++ chiselDeps;
            };

            # for running ci
            ci = mkLLVMShell {
              buildInputs = commonDeps ++ chiselDeps ++ emulatorDeps;
              env = {
                VERILATOR_EMULATOR_BIN_PATH = "${pkgs.verilator-emulator}/bin";
                TEST_CASE_DIR = "${pkgs.rvv-testcases-prebuilt}";
              };
            };

            # for general development
            default = mkLLVMShell {
              buildInputs = commonDeps ++ chiselDeps ++ emulatorDeps;
              env.TEST_CASE_DIR = "${pkgs.rvv-testcases}";
              inherit postHook;
            };

            # for general development but with prebuilt testcases
            default-prebuilt-cases = mkLLVMShell {
              buildInputs = commonDeps ++ chiselDeps ++ emulatorDeps;
              env.TEST_CASE_DIR = "${pkgs.rvv-testcases-prebuilt}";
              inherit postHook;
            };
          };

          formatter = pkgs.nixpkgs-fmt;
        }
      )
    // { inherit inputs; overlays.default = overlay; };
}
