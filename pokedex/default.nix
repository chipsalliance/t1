{
  lib,
  newScope,
  riscv-opcodes-src,
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
in
lib.makeScope newScope (
  scope:
  {
    # Simulator needs no comptime ISA
    simulator = scope.callPackage ./simulator/package.nix { };
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
