{ lib
, newScope

, rv32-stdenv
}:

let
  configFiles = builtins.attrNames (builtins.readDir ../../configs);
  configNames = builtins.map (lib.removeSuffix ".json") configFiles;
in

lib.makeScope newScope
  (self: {
    submodules = self.callPackage ./submodules.nix { };
    elaborator = self.callPackage ./elaborator.nix { };

    riscv-opcodes-src = self.submodules.sources.riscv-opcodes.src;

    rvv-codegen = self.callPackage ./testcases/rvv-codegen.nix { };
    testcase-env = {
      mkMlirCase = self.callPackage ./testcases/make-mlir-case.nix { stdenv = rv32-stdenv; };
      mkIntrinsicCase = self.callPackage ./testcases/make-intrinsic-case.nix { stdenv = rv32-stdenv; };
      mkAsmCase = self.callPackage ./testcases/make-asm-case.nix { stdenv = rv32-stdenv; };
      mkCodegenCase = self.callPackage ./testcases/make-codegen-case.nix { stdenv = rv32-stdenv; };
    };
    rvv-testcases = self.callPackage ../../tests { };
  } //
  lib.genAttrs configNames (configName:
    # by using makeScope, callPackage can send the following attributes to package parameters
    lib.makeScope self.newScope (innerSelf: {
      config-name = configName;
      elaborate-config = ../../configs/${configName}.json;

      ip = {
        rtl = innerSelf.callPackage ./elaborate.nix { target = "ip"; };

        emu-rtl = innerSelf.callPackage ./elaborate.nix { target = "ipemu"; };
        emu = innerSelf.callPackage ./ipemu.nix { rtl = innerSelf.ip.emu-rtl; };
        emu-trace = innerSelf.callPackage ./ipemu.nix { rtl = innerSelf.ip.emu-rtl; do-trace = true; };
      };

      subsystem = {
        rtl = innerSelf.callPackage ./elaborate.nix { target = "subsystem"; };

        emu-rtl = innerSelf.callPackage ./elaborate.nix { target = "subsystememu"; };
        emu = innerSelf.callPackage ./subsystememu.nix { rtl = innerSelf.subsystem.emu-rtl; };
        emu-trace = innerSelf.callPackage ./subsystememu.nix { rtl = innerSelf.subsystem.emu-rtl; do-trace = true; };

        fpga-rtl = innerSelf.callPackage ./elaborate.nix { target = "fpga"; };
      };
    })
  )
  )
