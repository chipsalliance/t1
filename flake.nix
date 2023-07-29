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
              # TODO: Currently, the emulator needs all the dependencies to run a test case ,
              # but most of them are used to get version information, so they should be cleaned up one day.
              buildInputs = commonDeps ++ chiselDeps ++ testcaseDeps ++ emulatorDeps;

              # Use function to avoid compiling testcase in same way everytime(without cache) entering the shell
              shellHook = ''
                echo "Notes: To setup or rebuild test case, run function 'testcase-setup' in bash"
                testcase-setup() {
                  local useTarball=$1; shift
                  local output=$PWD/tests-out
                  if (( $useTarball )); then
                    nix build --out-link $output .#testcase-from-src
                  else
                    nix build --out-link $output .#testcase
                  fi
                  export TEST_CASE_DIR=$output/bin
                }
              '';
            };
            emulator = mkLLVMShell {
              buildInputs = commonDeps ++ emulatorDeps;
            };
            default = mkLLVMShell {
              buildInputs = commonDeps ++ chiselDeps ++ testcaseDeps ++ emulatorDeps;
            };
          };

          # nix develop .#testcase && nix build .#test-case-from-src
          packages.testcase-from-src = pkgs.callPackage ./tests { useTarball = false; };
          packages.testcase = pkgs.callPackage ./tests { useTarball = true; };
        }
      )
    // { inherit inputs; overlays.default = overlay; };
}
