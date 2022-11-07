{
  description = "vector";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem
      (system:
      let
        pkgs = import nixpkgs { inherit system; };
        deps = pkgs.callPackage ./deps.nix {};
      in
        {
          defaultApp = pkgs.writeShellApplication {
            name = "recognize";
            runtimeInputs = deps;
            text = ''
              make test
            '';
          }; 
          devShell = pkgs.mkShell {
            runtimeInputs = deps;
            shellHook = ''
              # waiting for https://github.com/NixOS/nixpkgs/pull/192943
              export NIX_CC=" "
            '';
          };
        }
      );
}
