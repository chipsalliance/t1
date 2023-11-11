{ lib
, pkgs
, newScope

, llvmForDev
}:

let
  configFiles = builtins.attrNames (builtins.readDir ../../configs);
  configNames = builtins.map (lib.removeSuffix ".json") configFiles;
in

lib.makeScope newScope
  (self: {
    submodules = self.callPackage ./submodules.nix { };
    elaborator = self.callPackage ./elaborator.nix { };

    rvv-codegen = self.callPackage ./testcases/rvv-codegen.nix { };
    rvv-testcases = self.callPackage ./testcases/rvv-testcases.nix {
      stdenv = llvmForDev.stdenv;
      llvmPackages = llvmForDev;
    };
    rvv-testcases-prebuilt = self.callPackage ./testcases/rvv-testcases-prebuilt.nix {
      # clang is faster for compiling verilator emulator
      stdenv = llvmForDev.stdenv;
    };
  } //
  lib.genAttrs configNames (configName:
    # by using makeScope, callPackage can send the following attributes to package parameters
    lib.makeScope self.newScope (innerSelf: {
      elaborate-config = ../../configs/${configName}.json;

      elaborate = innerSelf.callPackage ./elaborate.nix { };

      verilator-emulator = innerSelf.callPackage ./verilator-emulator.nix { };
    })
  )
)
