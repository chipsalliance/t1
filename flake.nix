{
  description = "vector";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }@inputs:
    flake-utils.lib.eachDefaultSystem
      (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
        deps = pkgs.callPackage ./deps.nix {};
      in
        {
          devShell = pkgs.mkShellNoCC {
            buildInputs = deps;
            shellHook = ''
              # waiting for https://github.com/NixOS/nixpkgs/pull/192943
              export NIX_CC=" "
            '';
          };
        }
      )
    // { inherit inputs; };
}
