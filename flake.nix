{
  description = "vector";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    mill-ivy-fetcher.url = "github:Avimitin/mill-ivy-fetcher";
    zaozi.url = "github:sequencer/zaozi";
    treefmt-nix.url = "github:numtide/treefmt-nix";
  };

  outputs =
    {
      self,
      nixpkgs,
      mill-ivy-fetcher,
      flake-utils,
      zaozi,
      treefmt-nix,
    }@inputs:
    let
      overlay = import ./nix/overlay.nix { inherit self; };
    in
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs {
          inherit system;
          overlays = [
            mill-ivy-fetcher.overlays.default
            zaozi.overlays.default
            overlay
          ];
        };
        treefmtEval = treefmt-nix.lib.evalModule pkgs {
          projectRootFile = "flake.nix";
          settings.on-unmatched = "debug";
          programs.nixfmt.enable = true;
          programs.scalafmt.enable = true;
          programs.rustfmt.enable = true;
        };
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
        formatter = treefmtEval.config.build.wrapper;
        checks = {
          formatting = treefmtEval.config.build.check self;
        };
      }
    )
    // {
      inherit inputs;
      overlays.default = overlay;
    };
}
