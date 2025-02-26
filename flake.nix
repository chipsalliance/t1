{
  description = "vector";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    nixpkgs-for-circt.url = "github:NixOS/nixpkgs/nixos-unstable-small";
    chisel-nix.url = "github:chipsalliance/chisel-nix";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, chisel-nix, flake-utils, nixpkgs-for-circt }@inputs:
    let
      overlay = import ./nix/overlay.nix { inherit self; };
    in
    flake-utils.lib.eachDefaultSystem
      (system:
        let
          pkgs = import nixpkgs { inherit system; overlays = [ chisel-nix.overlays.mill-flows overlay ]; };
        in
        {
          legacyPackages = pkgs;
          devShells = {
            default = pkgs.mkShell {
              buildInputs = with pkgs; [
                ammonite
                mill
                t1-helper
                zstd
                xorg.lndir
              ];
              shellHook = pkgs.t1._t1MillModules.shellHook;
            };
          };
          formatter = pkgs.nixpkgs-fmt;
        }
      )
    // { inherit inputs; overlays.default = overlay; };
}
