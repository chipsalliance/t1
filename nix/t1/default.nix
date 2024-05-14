{ lib
, system
, stdenv
, useMoldLinker
, newScope

, rv32-stdenv
, runCommand
, pkgsX86

, allConfigs
}:

let
  moldStdenv = useMoldLinker stdenv;
in

lib.makeScope newScope
  (self:
  let
    _millOutput = self.callPackage ./t1.nix { };
  in
  {
    inherit allConfigs;

    elaborator = _millOutput.elaborator // { meta.mainProgram = "elaborator"; };
    configgen = _millOutput.configgen // { meta.mainProgram = "configgen"; };
    t1package = _millOutput.t1package;

    omreader = self.callPackage ./omreader.nix { };
    submodules = self.callPackage ./submodules.nix { };

    riscv-opcodes-src = self.submodules.sources.riscv-opcodes.src;

    rvv-codegen = self.callPackage ./testcases/rvv-codegen.nix { };

  } //
  lib.genAttrs allConfigs (configName:
    # by using makeScope, callPackage can send the following attributes to package parameters
    lib.makeScope self.newScope (innerSelf: rec {
      recurseForDerivations = true;

      # For package name concatenate
      inherit configName;

      elaborateConfigJson = runCommand "${configName}-config.json" { } ''
        ${self.configgen}/bin/configgen ${configName} -t .
        mv config.json $out
      '';

      _caseBuilders = {
        mkMlirCase = innerSelf.callPackage ./testcases/make-mlir-case.nix { stdenv = rv32-stdenv; };
        mkIntrinsicCase = innerSelf.callPackage ./testcases/make-intrinsic-case.nix { stdenv = rv32-stdenv; };
        mkAsmCase = innerSelf.callPackage ./testcases/make-asm-case.nix { stdenv = rv32-stdenv; };
        mkCodegenCase = innerSelf.callPackage ./testcases/make-codegen-case.nix { stdenv = rv32-stdenv; };
      };

      # Maybe dependent on config later
      linkerScript = ../../tests/t1.ld;

      cases = innerSelf.callPackage ../../tests { };

      # for the convenience to use x86 cases on non-x86 machines, avoiding the extra build time
      cases-x86 =
        if system == "x86-64-linux"
        then self.cases
        else pkgsX86.t1."${configName}".cases;

      ip = rec {
        recurseForDerivations = true;

        elaborate = innerSelf.callPackage ./elaborate.nix { target = "ip"; /* use-binder = true; */ };
        mlirbc = innerSelf.callPackage ./mlirbc.nix { inherit elaborate; };
        rtl = innerSelf.callPackage ./rtl.nix { inherit mlirbc; };
        omreaderlib = innerSelf.callPackage ./omreaderlib.nix { inherit mlirbc; };

        emu-elaborate = innerSelf.callPackage ./elaborate.nix { target = "ipemu"; /* use-binder = true; */ };
        emu-mlirbc = innerSelf.callPackage ./mlirbc.nix { elaborate = emu-elaborate; };
        emu-rtl = innerSelf.callPackage ./rtl.nix { mlirbc = emu-mlirbc; };

        emu = innerSelf.callPackage ./ipemu.nix { rtl = ip.emu-rtl; stdenv = moldStdenv; };
        emu-trace = innerSelf.callPackage ./ipemu.nix { rtl = emu-rtl; stdenv = moldStdenv; do-trace = true; };

        t1-simulator = innerSelf.callPackage ../../difftest/t1-simulator/default.nix { rtl = innerSelf.ip.emu-rtl; };
      };

      subsystem = rec {
        recurseForDerivations = true;

        elaborate = innerSelf.callPackage ./elaborate.nix { target = "subsystem"; /* use-binder = true; */ };
        mlirbc = innerSelf.callPackage ./mlirbc.nix { inherit elaborate; };
        rtl = innerSelf.callPackage ./rtl.nix { inherit mlirbc; };
      };

      release = innerSelf.callPackage ./release { };
    })
  )
  )
