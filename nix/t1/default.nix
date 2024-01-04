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

    makeTestArtifacts = self.callPackage ./testcases/make-test-artifacts.nix { };
  } //
  lib.genAttrs configNames (configName:
    # by using makeScope, callPackage can send the following attributes to package parameters
    lib.makeScope self.newScope (innerSelf: {
      recurseForDerivations = true;

      config-name = configName;
      elaborate-config = ../../configs/${configName}.json;

      ip = {
        recurseForDerivations = true;

        rtl = innerSelf.callPackage ./elaborate.nix { target = "ip"; };

        emu-rtl = innerSelf.callPackage ./elaborate.nix { target = "ipemu"; };
        emu = innerSelf.callPackage ./ipemu.nix { rtl = innerSelf.ip.emu-rtl; };
        emu-trace = innerSelf.callPackage ./ipemu.nix { rtl = innerSelf.ip.emu-rtl; do-trace = true; };
      };

      subsystem = {
        recurseForDerivations = true;

        rtl = innerSelf.callPackage ./elaborate.nix { target = "subsystem"; };

        emu-rtl = innerSelf.callPackage ./elaborate.nix { target = "subsystememu"; };
        emu = innerSelf.callPackage ./subsystememu.nix { rtl = innerSelf.subsystem.emu-rtl; };
        emu-trace = innerSelf.callPackage ./subsystememu.nix { rtl = innerSelf.subsystem.emu-rtl; do-trace = true; };

        fpga-rtl = innerSelf.callPackage ./elaborate.nix { target = "fpga"; };
      };

      _rvvTestCaseExecutors =
        let
          /*
            Turn all the test cases derivation into a set of lambda, with each of them when filling the argument can produce a test case outputs.

            Example:
              mapTestCaseToBuilder { codegen = { vaadd-vv = <drv>; }; }

              => { codegen = { vaadd-vv = { args }: derivation } }

            Type:
              mapTestCaseToBuilder :: { caseType :: { caseName :: <derivation> } } -> { caseType :: { caseName :: (AttrSet -> <derivation>) } }
          */
          mapTestCaseToBuilder = with lib; with builtins;
            attr: pipe attr [
              # Filter out some derived functions
              (filterAttrs
                (_: v: typeOf v == "set"))
              # Filter out our magic "all" derivation
              (filterAttrs (name: _: name != "all"))
              # Filter out the magic nix search attribute
              (filterAttrsRecursive (name: _: name != "recurseForDerivations"))
              # Transform all the derivation into lambda
              (mapAttrs
                (_: case:
                  mapAttrs
                    (_: caseDrv: self.makeTestArtifacts caseDrv)
                    case))
            ];
        in
        mapTestCaseToBuilder self.rvv-testcases;
    })
  )
  )
