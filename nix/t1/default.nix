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

    rocketv = self.callPackage ../../rocketemu { };

    t1rocket = self.callPackage ../../t1rocketemu { };

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

        ip = lib.makeScope innerSelf.newScope (ipSelf: {
          recurseForDerivations = true;

          # T1 RTL.
          elaborate = ipSelf.callPackage ./elaborate.nix { target = "ip"; };
          mlirbc = ipSelf.callPackage ./mlirbc.nix { };
          rtl = ipSelf.callPackage ./rtl.nix {
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

          om = ipSelf.callPackage ./om.nix { };
          omreader = self.omreader-unwrapped.mkWrapper { };

          emu-om = ipSelf.callPackage ./om.nix { mlirbc = ipSelf.emu-mlirbc; };
          emu-omreader = self.omreader-unwrapped.mkWrapper { mlirbc = ipSelf.emu-mlirbc; };
          omGet = args: lib.fileContents (runCommand "get-${args}" { } ''
            ${ipSelf.emu-omreader}/bin/omreader ${args} > $out
          '');
          rtlDesignMetadata = with ipSelf; rec {
            march = omGet "march";
            extensions = builtins.fromJSON (omGet "extensionsJson");
            vlen = omGet "vlen";
            dlen = omGet "dlen";
            xlen = if (lib.hasPrefix "rv32" march) then 32 else 64;
          };

          emu-elaborate = ipSelf.callPackage ./elaborate.nix { target = "ipemu"; };
          emu-mlirbc = ipSelf.callPackage ./mlirbc.nix { elaborate = ipSelf.emu-elaborate; };

          # T1 Verilator Emulator
          verilator-emu-omreader = self.omreader-unwrapped.mkWrapper { mlirbc = ipSelf.emu-mlirbc; };
          verilator-emu-rtl = ipSelf.callPackage ./rtl.nix {
            mlirbc = ipSelf.emu-mlirbc;
            mfcArgs = lib.escapeShellArgs [
              "-O=release"
              "--split-verilog"
              "--preserve-values=all"
              "--verification-flavor=if-else-fatal"
              "--lowering-options=verifLabels,omitVersionComment"
              "--strip-debug-info"
            ];
          };
          verilator-emu-rtl-verilated = ipSelf.callPackage ./verilated.nix { stdenv = moldStdenv; };
          verilator-emu-rtl-verilated-trace = ipSelf.callPackage ./verilated.nix { stdenv = moldStdenv; enable-trace = true; };

          verilator-emu = ipSelf.callPackage ../../difftest/verilator.nix { };
          verilator-emu-trace = ipSelf.callPackage ../../difftest/verilator.nix { verilator-emu-rtl-verilated = ipSelf.verilator-emu-rtl-verilated-trace; };

          # T1 VCS Emulator
          vcs-emu-omreader = self.omreader-unwrapped.mkWrapper { mlirbc = ipSelf.emu-mlirbc; };
          vcs-emu-rtl = ipSelf.callPackage ./rtl.nix {
            mlirbc = ipSelf.emu-mlirbc;
            mfcArgs = lib.escapeShellArgs [
              "-O=release"
              "--split-verilog"
              "--preserve-values=all"
              "--verification-flavor=sva"
              "--lowering-options=verifLabels,omitVersionComment"
              "--strip-debug-info"
            ];
          };
          vcs-dpi-lib = ipSelf.callPackage ../../difftest/vcs.nix { };
          vcs-dpi-lib-trace = ipSelf.vcs-dpi-lib.override { enable-trace = true; };
          # FIXME: vcs-emu should have offline check instead of using verilator one
          vcs-emu = ipSelf.callPackage ./vcs.nix { };
          vcs-emu-trace = ipSelf.callPackage ./vcs.nix { vcs-dpi-lib = ipSelf.vcs-dpi-lib-trace; };

          offline = ipSelf.callPackage ../../difftest/offline.nix { };
        });

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
