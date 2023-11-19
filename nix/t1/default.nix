{ lib
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
    soc-elaborator = self.callPackage ./soc-elaborator.nix { };

    rvv-codegen = self.callPackage ./testcases/rvv-codegen.nix { };
    rvv-testcases-prebuilt = self.callPackage ./testcases/rvv-testcases-prebuilt.nix { };
    testcase-env = {
      mkMlirCase = self.callPackage ./testcases/make-mlir-case.nix { };
      mkIntrinsicCase = self.callPackage ./testcases/make-intrinsic-case.nix { };
      mkAsmCase = self.callPackage ./testcases/make-asm-case.nix { };
      mkCodegenCase = self.callPackage ./testcases/make-codegen-case.nix { };
    };
    rvv-testcases = self.callPackage ../../tests { };
  } //
  lib.genAttrs configNames (configName:
    # by using makeScope, callPackage can send the following attributes to package parameters
    lib.makeScope self.newScope (innerSelf: {
      config-name = configName;
      elaborate-config = ../../configs/${configName}.json;

      elaborate = innerSelf.callPackage ./elaborate.nix { };
      elaborate-release = innerSelf.callPackage ./elaborate.nix { is-testbench = false; };

      verilator-emulator = innerSelf.callPackage ./verilator-emulator.nix { };
      verilator-emulator-trace = innerSelf.callPackage ./verilator-emulator.nix { do-trace = true; };

      soc-elaborate = innerSelf.callPackage ./soc-elaborate.nix { };
      soc-verilator-emulator = innerSelf.callPackage ./soc-verilator-emulator.nix { };
      soc-verilator-emulator-trace = innerSelf.callPackage ./soc-verilator-emulator.nix { do-trace = true; };
    })
  )
  )
