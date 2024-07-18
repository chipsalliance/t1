{ lib
, system
, stdenv
, useMoldLinker
, newScope

, pkgsX86
}:

let
  moldStdenv = useMoldLinker stdenv;

  configsDirectory = ../../configgen/generated;

  # allConfigs is a (configName -> configJsonPath) map
  allConfigs = lib.mapAttrs'
    (fileName: fileType:
      assert fileType == "regular" && lib.hasSuffix ".json" fileName;
      lib.nameValuePair
        (lib.removeSuffix ".json" fileName)
        (lib.path.append configsDirectory fileName))
    (builtins.readDir configsDirectory);
in
lib.makeScope newScope
  (self:
  let
    _millOutput = self.callPackage ./t1.nix { };
  in
  {
    inherit allConfigs;
    recurseForDerivations = true;

    elaborator = _millOutput.elaborator // { meta.mainProgram = "elaborator"; };
    configgen = _millOutput.configgen // { meta.mainProgram = "configgen"; };
    t1package = _millOutput.t1package;

    omreader-unwrapped = self.callPackage ./omreader.nix { };
    submodules = self.callPackage ./submodules.nix { };

    riscv-opcodes-src = self.submodules.sources.riscv-opcodes.src;
  } //
  lib.mapAttrs
    (configName: configPath:
      # by using makeScope, callPackage can send the following attributes to package parameters
      lib.makeScope self.newScope (innerSelf: rec {
        recurseForDerivations = true;

        # For package name concatenate
        inherit configName;

        elaborateConfigJson = configPath;
        elaborateConfig = builtins.fromJSON (lib.readFile configPath);

        cases = innerSelf.callPackage ../../tests { difftest = ip.difftest; difftest-trace = ip.difftest-trace; };

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

          omreader = self.omreader-unwrapped.mkWrapper { inherit mlirbc; };

          om = innerSelf.callPackage ./om.nix { inherit mlirbc; };

          emu-elaborate = innerSelf.callPackage ./elaborate.nix { target = "ipemu"; /* use-binder = true; */ };
          emu-mlirbc = innerSelf.callPackage ./mlirbc.nix { elaborate = emu-elaborate; };
          emu-omreader = self.omreader-unwrapped.mkWrapper { mlirbc = emu-mlirbc; };
          emu-rtl = innerSelf.callPackage ./rtl.nix { mlirbc = emu-mlirbc; };

          verilated = innerSelf.callPackage ./verilated.nix { rtl = emu-rtl; };
          verilated-trace = innerSelf.callPackage ./verilated.nix { rtl = emu-rtl; enable-trace = true; };

          emu = innerSelf.callPackage ./ipemu.nix { rtl = ip.emu-rtl; stdenv = moldStdenv; };
          emu-trace = innerSelf.callPackage ./ipemu.nix { rtl = emu-rtl; stdenv = moldStdenv; do-trace = true; };

          difftest = innerSelf.callPackage ../../difftest/default.nix { inherit verilated; };
          difftest-trace = innerSelf.callPackage ../../difftest/default.nix { verilated = verilated-trace; };
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
    allConfigs
  )
