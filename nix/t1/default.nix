{ lib
, newScope
}:

let
  configFiles = builtins.attrNames (builtins.readDir ../../configs);
  configNames = builtins.map (lib.removeSuffix ".json") configFiles;
in

lib.genAttrs configNames (configName:
# by using makeScope, callPackage can send the following attributes to package parameters
lib.makeScope newScope (self: {
  elaborate-config = ../../configs/${configName}.json;

  elaborator-jar = self.callPackage ./elaborator-jar.nix { };

  elaborate = self.callPackage ./elaborate.nix { };

  verilator-emulator = self.callPackage ./verilator-emulator.nix { };
})
)
