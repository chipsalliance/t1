{
  description = "vector";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    nixpkgs-for-circt.url = "github:NixOS/nixpkgs/nixos-unstable-small";
    flake-utils.url = "github:numtide/flake-utils";
    nix-bundle = {
      url = "github:Avimitin/nix-bundle/pname-fix";
      inputs.nixpkgs.follows = "nixpkgs";
      inputs.utils.follows = "flake-utils";
    };
  };

  outputs = { self, nixpkgs, flake-utils, nixpkgs-for-circt, nix-bundle }@inputs:
    let
      overlay = import ./nix/overlay.nix { inherit self; };
    in
    flake-utils.lib.eachDefaultSystem
      (system:
        let
          pkgs = import nixpkgs { inherit system; overlays = [ overlay ]; };
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
          bundlers = rec {
            default = toArx;
            toArx = nix-bundle.bundlers.${system}.nix-bundle;
          };
        }
      )
    // { inherit inputs; overlays.default = overlay; };
}
