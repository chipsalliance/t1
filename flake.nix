{
  description = "vector";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }@inputs:
    let
      overlay = import ./overlay.nix;
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

          testcaseDeps = with pkgs; [
            rv32-clang
            glibc_multi
            llvmForDev.bintools
            go
            buddy-mlir
            rvv-codegen
          ];

          emulatorDeps = with pkgs; [
            cmake
            libargs
            spdlog
            fmt
            (enableDebugging libspike)
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
            chisel = pkgs.mkShell {
              buildInputs = commonDeps ++ chiselDeps;
            };
            # This devShell is used for build && run testcase
            testcase = mkLLVMShell {
              buildInputs = commonDeps ++ chiselDeps ++ testcaseDeps ++ emulatorDeps;

              env = {
                CODEGEN_BIN_PATH = "${pkgs.rvv-codegen}/bin/single";
                CODEGEN_INC_PATH = "${pkgs.rvv-codegen}/include";
                CODEGEN_CFG_PATH = "${pkgs.rvv-codegen}/configs";
              };
            };
            # Used only for run testcase
            testcase-prebuilt = mkLLVMShell {
              buildInputs = commonDeps ++ chiselDeps ++ emulatorDeps;

              env = {
                # use default devShell to build testcase
                TEST_CASE_DIR = "${pkgs.rvv-testcase-prebuilt}";
              };
            };
            ci = mkLLVMShell {
              buildInputs = commonDeps ++ chiselDeps ++ emulatorDeps;
              env = let
                verilator-emulator = pkgs.callPackage ./nix/verilator-emulator.nix { emulatorSrc = ./.; };
              in
              {
                VERILATOR_EMULATOR_BIN_PATH = "${verilator-emulator}/bin";
                TEST_CASE_DIR = "${pkgs.rvv-testcase}";
              };
            };
            emulator = mkLLVMShell {
              buildInputs = commonDeps ++ chiselDeps ++ emulatorDeps;

              inherit postHook;
            };
            default = mkLLVMShell {
              buildInputs = commonDeps ++ chiselDeps ++ emulatorDeps;

              env = {
                # use default devShell to build testcase
                TEST_CASE_DIR = "${pkgs.rvv-testcase-prebuilt}";
              };

              inherit postHook;
            };
          };

          formatter = pkgs.nixpkgs-fmt;
        }
      )
    // { inherit inputs; overlays.default = overlay; };
}
