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
            # for test case build script
            mill
            ammonite
          ];

          emulatorDeps = with pkgs; [
            cmake
            libargs
            glog
            fmt
            (enableDebugging libspike)
            jsoncpp.dev
            ninja

            # for verilator
            verilator
            zlib

            # for CI
            ammonite
          ];

          mkLLVMShell = pkgs.mkShell.override { stdenv = pkgs.llvmForDev.stdenv; };
        in
        {
          legacyPackages = pkgs;
          devShells = {
            chisel = pkgs.mkShell {
              buildInputs = commonDeps ++ chiselDeps;
            };
            testcase = mkLLVMShell {
              buildInputs = commonDeps ++ testcaseDeps;

              shellHook = let
                test-artifact = pkgs.callPackage ./tests {};
              in ''
                export TESTS_OUT_DIR=${test-artifact}
              '';
            };
            emulator = mkLLVMShell {
              buildInputs = commonDeps ++ emulatorDeps;
            };
            default = mkLLVMShell {
              buildInputs = commonDeps ++ chiselDeps ++ testcaseDeps ++ emulatorDeps;
            };
          };
        }
      )
    // { inherit inputs; overlays.default = overlay; };
}
