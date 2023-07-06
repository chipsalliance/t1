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
          deps = with pkgs; [
            rv32-clang
            glibc_multi
            llvmForDev.bintools

            cmake
            libargs
            glog
            fmt
            (enableDebugging libspike)
            zlib
            jsoncpp.dev

            mill
            python3
            go
            ammonite
            metals
            gnused
            coreutils
            gnumake
            gnugrep
            which
            parallel
            protobuf
            ninja
            verilator
            antlr4
            numactl
            dtc
            espresso
            circt
            buddy-mlir

            yarn
            mdl
          ];
        in
        {
          legacyPackages = pkgs;
          devShell = pkgs.mkShell.override { stdenv = pkgs.llvmForDev.stdenv; } {
            buildInputs = deps;
          };
        }
      )
    // { inherit inputs; overlays.default = overlay; };
}
