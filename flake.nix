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
        in
        {
          legacyPackages = pkgs;
          devShells = rec {
            default = pkgs.mkShell {
              buildInputs = with pkgs; [
                gnumake
                gnugrep
                gnused

                mill
                ammonite
              ];
            };

            with-prebuilt-cases = default.overrideAttrs (_: {
              env.TEST_CASES_DIR = pkgs.t1.rvv-testcases-prebuilt;
            });

            ci = pkgs.mkShellNoCC {
              buildInputs = with pkgs; [ ammonite python3 ];
              env.TEST_CASES_DIR = pkgs.t1.rvv-testcases;
            };
          };

          formatter = pkgs.nixpkgs-fmt;
        }
      )
    // { inherit inputs; overlays.default = overlay; };
}
