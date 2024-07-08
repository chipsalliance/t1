{ lib
, system
, stdenv
, useMoldLinker
, newScope
, runCommand

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

    # FIXME: move all the rocketv file to an individual directory and put all attribute into one scope.
    rocketv-mlirbc = self.callPackage ./rocketv-mlirbc.nix { };
    rocketv-rtl = self.callPackage ./rocketv-rtl.nix { };
    rocketv-verilated-csrc = self.callPackage ./rocketv-verilated-csrc.nix { };
    rocketv-emu = self.callPackage ../../rocketemu { };

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

        ip = rec {
          recurseForDerivations = true;

          # T1 RTL.
          elaborate = innerSelf.callPackage ./elaborate.nix { target = "ip"; };
          mlirbc = innerSelf.callPackage ./mlirbc.nix { inherit elaborate; };
          rtl = innerSelf.callPackage ./rtl.nix {
            inherit mlirbc;
            mfcArgs = lib.escapeShellArgs [
              "-O=release"
              "--disable-all-randomization"
              "--split-verilog"
              "--preserve-values=all"
              "--strip-debug-info"
              "--strip-fir-debug-info"
              "--verification-flavor=sva"
              "--lowering-options=verifLabels,omitVersionComment,emittedLineLength=240,locationInfoStyle=none"
            ];
          };

          om = innerSelf.callPackage ./om.nix { inherit mlirbc; };
          omreader = self.omreader-unwrapped.mkWrapper { inherit mlirbc; };

          emu-om = innerSelf.callPackage ./om.nix { mlirbc = emu-mlirbc; };
          emu-omreader = self.omreader-unwrapped.mkWrapper { mlirbc = emu-mlirbc; };
          omGet = args: lib.fileContents (runCommand "get-${args}" { } ''
            ${emu-omreader}/bin/omreader ${args} > $out
          '');
          rtlDesignMetadata = {
            march = omGet "march";
            extensions = builtins.fromJSON (omGet "extensionsJson");
            vlen = omGet "vlen";
            dlen = omGet "dlen";
          };

          cases = innerSelf.callPackage ../../tests {
            inherit (ip) verilator-emu verilator-emu-trace vcs-emu vcs-emu-trace rtlDesignMetadata;
          };

          # for the convenience to use x86 cases on non-x86 machines, avoiding the extra build time
          cases-x86 =
            if system == "x86-64-linux"
            then self.cases
            else pkgsX86.t1."${configName}".cases;

          emu-elaborate = innerSelf.callPackage ./elaborate.nix { target = "ipemu"; };
          emu-mlirbc = innerSelf.callPackage ./mlirbc.nix { elaborate = emu-elaborate; };

          # T1 Verilator Emulator
          verilator-emu-omreader = self.omreader-unwrapped.mkWrapper { mlirbc = emu-mlirbc; };
          verilator-emu-rtl = innerSelf.callPackage ./rtl.nix {
            mlirbc = emu-mlirbc;
            mfcArgs = lib.escapeShellArgs [
              "-O=release"
              "--split-verilog"
              "--preserve-values=all"
              "--verification-flavor=if-else-fatal"
              "--lowering-options=verifLabels,omitVersionComment"
              "--strip-debug-info"
            ];
          };
          verilator-emu-rtl-verilated = innerSelf.callPackage ./verilated.nix { rtl = verilator-emu-rtl; stdenv = moldStdenv; };
          verilator-emu-rtl-verilated-trace = innerSelf.callPackage ./verilated.nix { rtl = verilator-emu-rtl; stdenv = moldStdenv; enable-trace = true; };
          verilator-emu = innerSelf.callPackage ../../difftest/verilator.nix { verilated = verilator-emu-rtl-verilated; };
          verilator-emu-trace = innerSelf.callPackage ../../difftest/verilator.nix { verilated = verilator-emu-rtl-verilated-trace; };

          # T1 VCS Emulator
          vcs-emu-omreader = self.omreader-unwrapped.mkWrapper { mlirbc = emu-mlirbc; };
          vcs-emu-rtl = innerSelf.callPackage ./rtl.nix {
            mlirbc = emu-mlirbc;
            mfcArgs = lib.escapeShellArgs [
              "-O=release"
              "--split-verilog"
              "--preserve-values=all"
              "--verification-flavor=sva"
              "--lowering-options=verifLabels,omitVersionComment"
              "--strip-debug-info"
            ];
          };
          vcs-dpi-lib = innerSelf.callPackage ../../difftest/online_vcs { };
          vcs-dpi-lib-trace = vcs-dpi-lib.override { enable-trace = true; };
          vcs-emu = innerSelf.callPackage ./vcs.nix { inherit vcs-dpi-lib; rtl = vcs-emu-rtl; };
          vcs-emu-trace = innerSelf.callPackage ./vcs.nix { vcs-dpi-lib = vcs-dpi-lib-trace; rtl = vcs-emu-rtl; };
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
