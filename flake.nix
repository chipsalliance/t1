{
  description = "vector";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  nixConfig = {
    sandbox = "relaxed";
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
            rv32-gnu-toolchain
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
            # This environment is provided for writing and compiling testcase.
            # If you are going to run test cases, use the .#testcase devShell.
            testcase-bootstrap = mkLLVMShell {
              buildInputs = commonDeps ++ testcaseDeps ++ [ pkgs.ammonite pkgs.mill ];

              env = {
                CODEGEN_BIN_PATH = "${pkgs.rvv-codegen}/bin/single";
                CODEGEN_INC_PATH = "${pkgs.rvv-codegen}/include";
                CODEGEN_CFG_PATH = "${pkgs.rvv-codegen}/configs";
              };
            };
            # This devShell is used for running testcase
            testcase = mkLLVMShell {
              # TODO: Currently, the emulator needs all the dependencies to run a test case ,
              # but most of them are used to get version information, so they should be cleaned up one day.
              buildInputs = commonDeps ++ chiselDeps ++ testcaseDeps ++ emulatorDeps ++ [ pkgs.metals ];

              env = {
                TEST_CASE_DIR = "${pkgs.rvv-testcase}";
              };
            };
            emulator = mkLLVMShell {
              buildInputs = commonDeps ++ chiselDeps ++ emulatorDeps;

              inherit postHook;
            };
            default = mkLLVMShell {
              buildInputs = commonDeps ++ chiselDeps ++ testcaseDeps ++ emulatorDeps;
              env = {
                CODEGEN_BIN_PATH = "${pkgs.rvv-codegen}/bin/single";
                CODEGEN_INC_PATH = "${pkgs.rvv-codegen}/include";
                CODEGEN_CFG_PATH = "${pkgs.rvv-codegen}/configs";
                TEST_CASE_DIR = "${pkgs.rvv-testcase}";
              };
              inherit postHook;
            };
          };

          # nix build .#testcase
          packages.testcase = pkgs.callPackage ./nix/rvv-testcase.nix { };
        }
      )
    // { inherit inputs; overlays.default = overlay; };
}
