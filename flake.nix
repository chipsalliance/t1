{
  description = "vector";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    nixpkgs-for-circt.url = "github:NixOS/nixpkgs/nixos-unstable-small";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils, nixpkgs-for-circt }@inputs:
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
            # TODO: The dev shell will only depends on the T1 script package, let it manage different dev/ci/release flows.
            default = pkgs.mkShell {
              buildInputs = with pkgs; [
                ammonite
                # To develop T1-script, run nix develop .#t1-script.withLsp
                t1-script
              ];
            };
          };
          formatter = pkgs.nixpkgs-fmt;
        }
      )
    // { inherit inputs; overlays.default = overlay; };
}
