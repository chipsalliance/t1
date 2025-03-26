{
  description = "vector";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    mill-ivy-fetcher = {
      url = "github:Avimitin/mill-ivy-fetcher";
      inputs.nixpkgs.follows = "nixpkgs";
      inputs.flake-utils.follows = "flake-utils";
    };
    zaozi.url = "github:sequencer/zaozi";
  };

  outputs = { self, nixpkgs, mill-ivy-fetcher, flake-utils, zaozi }@inputs:
    let
      overlay = import ./nix/overlay.nix { inherit self; };
    in
    flake-utils.lib.eachDefaultSystem
      (system:
        let
          pkgs = import nixpkgs { inherit system; overlays = [ mill-ivy-fetcher.overlays.default zaozi.overlays.default overlay ]; };
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
              ];
            };
          };
          formatter = pkgs.nixpkgs-fmt;
        }
      )
    // { inherit inputs; overlays.default = overlay; };
}
