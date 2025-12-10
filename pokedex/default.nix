{
  lib,
  newScope,
  riscv-opcodes-src,
  fetchFromGitHub,
}:
let
  mkScope =
    parentScope: builder:
    with lib;
    builtins.readDir ./model/configs
    |> filterAttrs (k: v: hasSuffix ".toml" k && v == "regular")
    |> mapAttrs' (
      fp: _:
      nameValuePair (removeSuffix ".toml" fp) (
        lib.makeScope parentScope (
          scope:
          builder rec {
            inherit scope;
            configsPath = ./model/configs/${fp};
            configs = importTOML configsPath;
          }
        )
      )
    );

  pkgSrc = fetchFromGitHub {
    owner = "NixOS";
    repo = "nixpkgs";
    rev = "677fbe97984e7af3175b6c121f3c39ee5c8d62c9"; # nixpkgs-unstable-small 2025-12-09
    hash = "sha256-g2a4MhRKu4ymR4xwo+I+auTknXt/+j37Lnf0Mvfl1rE=";
  };
  lockedNixpkgs = import pkgSrc { system = "x86_64-linux"; };
in
lib.makeScope newScope (
  scope:
  {
    # Simulator needs no comptime ISA
    simulator = scope.callPackage ./simulator/package.nix { };

    # I need latest minijinja-cli and typst but doesn't want to bump main Nixpkgs.
    inherit (lockedNixpkgs) minijinja typst;
  }
  // mkScope scope.newScope (
    {
      configs,
      configsPath,
      scope,
    }:
    {
      inherit riscv-opcodes-src;

      pokedex-configs = configs // {
        src = configsPath;
      };

      model = scope.callPackage ./model/package.nix { };

      tests = scope.callPackage ./tests { };
      docs = scope.callPackage ./docs/package.nix { };
    }
  )
)
