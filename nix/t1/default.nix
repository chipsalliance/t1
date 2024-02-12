{ lib
, system
, newScope

, rv32-stdenv
, runCommand
, pkgsX86
}:

let
  # We need to bring submodules and configgen out of scope. Using them in scope to generate the package attribute set
  # will lead to infinite recursion.
  allConfigs = [
    "v4096-l32-b4-fp"
    "v4096-l32-b4"
    "v4096-l8-b4-fp"
    "v4096-l8-b4"
    "v1024-l8-b2-fp"
    "v1024-l8-b2"
    "v1024-l2-b2"
    "v1024-l1-b2"
  ];
in

lib.makeScope newScope
  (self:
  rec {
    _millOutput = self.callPackage ./t1.nix { };

    elaborator = _millOutput.elaborator // { meta.mainProgram = "elaborator"; };
    configgen = _millOutput.configgen // { meta.mainProgram = "configgen"; };

    submodules = self.callPackage ./submodules.nix { };

    riscv-opcodes-src = self.submodules.sources.riscv-opcodes.src;

    rvv-codegen = self.callPackage ./testcases/rvv-codegen.nix { };
    testcase-env = {
      mkMlirCase = self.callPackage ./testcases/make-mlir-case.nix { stdenv = rv32-stdenv; };
      mkIntrinsicCase = self.callPackage ./testcases/make-intrinsic-case.nix { stdenv = rv32-stdenv; };
      mkAsmCase = self.callPackage ./testcases/make-asm-case.nix { stdenv = rv32-stdenv; };
      mkCodegenCase = self.callPackage ./testcases/make-codegen-case.nix { stdenv = rv32-stdenv; };
    };
    makeTestCase = { xLen, vLen }: self.callPackage ../../tests { inherit xLen vLen; };

  } //
  lib.genAttrs allConfigs (configName:
    # by using makeScope, callPackage can send the following attributes to package parameters
    lib.makeScope self.newScope (innerSelf: rec {
      recurseForDerivations = true;

      # For package name concatenate
      inherit configName;

      elaborate-config = runCommand "emit-${configName}-config" { } ''
        mkdir -p $out
        ${self.configgen}/bin/configgen ${lib.concatStrings (lib.splitString "-" configName)} -t $out
      '';

      _elaborateConfig = with builtins; fromJSON (readFile "${elaborate-config}/config.json");

      cases = self.makeTestCase { inherit (_elaborateConfig.parameter) xLen vLen; };

      cases-x86 =
        if system == "x86-64-linux"
        then self.cases
        else pkgsX86.t1."${configName}".cases;

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
        emu-rtl = innerSelf.callPackage ./rtl.nix { mlirbc = innerSelf.subsystem.emu-mlirbc; };

        emu = innerSelf.callPackage ./subsystememu.nix { rtl = innerSelf.subsystem.emu-rtl; };
        emu-trace = innerSelf.callPackage ./subsystememu.nix { rtl = innerSelf.subsystem.emu-rtl; do-trace = true; };

        fpga-mlirbc = innerSelf.callPackage ./mlirbc.nix { target = "fpga"; };
        fpga-rtl = innerSelf.callPackage ./rtl.nix { mlirbc = innerSelf.subsystem.fpga-rtl; };
      };
    })
  )
  )
