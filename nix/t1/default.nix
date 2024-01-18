{ lib
, newScope

, rv32-stdenv
, callPackage
, runCommand
}:

let
  # We need to bring submodules and configgen out of scope. Using them in scope to generate the package attribute set
  # will lead to infinite recursion.
  submodules = callPackage ./submodules.nix { };
  configgen = callPackage ./configgen.nix { inherit submodules; };
  allConfigs = builtins.fromJSON (builtins.readFile "${configgen}/share/all-supported-configs.json");
in

lib.makeScope newScope
  (self:
  {
    elaborator = self.callPackage ./elaborator.nix { };

    inherit submodules configgen;

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
  lib.genAttrs allConfigs (configName:
    # by using makeScope, callPackage can send the following attributes to package parameters
    lib.makeScope self.newScope (innerSelf: rec {
      recurseForDerivations = true;

      # For package name concatenate
      inherit configName;

      elaborate-config = runCommand "emit-${configName}-config" { } ''
        mkdir -p $out
        ${configgen}/bin/configgen ${lib.concatStrings (lib.splitString "-" configName)} -t $out
      '';

      ip = {
        recurseForDerivations = true;

        mlirbc = innerSelf.callPackage ./mlirbc.nix { target = "ip"; };
        rtl = innerSelf.callPackage ./rtl.nix { mlirbc = innerSelf.ip.mlirbc; };

        emu-mlirbc = innerSelf.callPackage ./mlirbc.nix { target = "ipemu"; };
        emu-rtl = innerSelf.callPackage ./rtl.nix { mlirbc = innerSelf.ip.emu-mlirbc; };

        emu = innerSelf.callPackage ./ipemu.nix { rtl = innerSelf.ip.emu-rtl; };
        emu-trace = innerSelf.callPackage ./ipemu.nix { rtl = innerSelf.ip.emu-rtl; do-trace = true; };
      };

      subsystem = {
        recurseForDerivations = true;

        mlirbc = innerSelf.callPackage ./mlirbc.nix { target = "subsystem"; };
        rtl = innerSelf.callPackage ./rtl.nix { mlirbc = innerSelf.subsystem.mlirbc; };

        emu-mlirbc = innerSelf.callPackage ./mlirbc.nix { target = "subsystememu"; };
        emu-rtl = innerSelf.callPackage ./rtl.nix { mlirbc = innerSelf.subsystem.mlirbc; };

        emu = innerSelf.callPackage ./subsystememu.nix { rtl = innerSelf.subsystem.emu-rtl; };
        emu-trace = innerSelf.callPackage ./subsystememu.nix { rtl = innerSelf.subsystem.emu-rtl; do-trace = true; };

        fpga-mlirbc = innerSelf.callPackage ./mlirbc.nix { target = "fpga"; };
        fpga-rtl = innerSelf.callPackage ./rtl.nix { mlirbc = innerSelf.subsystem.fpga-rtl; };
      };
    })
  )
  )
