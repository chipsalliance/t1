{ lib, allConfigs, t1Scope, runCommand }:

# return attribute set with following hierarchy:
# {
#   "blastoise": { ip = { mlirbc = ...; vcs-emu = ...; ... } }
#   "...": { ip = { mlirbc = ...; vcs-emu = ...; ... } }
# }
lib.mapAttrs
  (configName: configPath:
  # by using makeScope, callPackage can send the following attributes to package parameters
  lib.makeScope t1Scope.newScope (cfgScope: rec {
    recurseForDerivations = true;

    # For package name concatenate
    inherit configName;

    elaborateConfigJson = configPath;
    elaborateConfig = builtins.fromJSON (lib.readFile configPath);

    ip = lib.makeScope cfgScope.newScope (ipScope: {
      recurseForDerivations = true;

      cases = ipScope.callPackage ../../tests { };

      # ---------------------------------------------------------------------------------
      # T1 only, without test bench
      # ---------------------------------------------------------------------------------
      mlirbc = t1Scope.chisel-to-mlirbc {
        outputName = "t1-non-testbench.mlirbc";
        elaboratorArgs = [
          "ip"
          "--ip-config"
          "${elaborateConfigJson}"
        ];
      };

      lowered-mlirbc = t1Scope.finalize-mlirbc {
        outputName = "lowered-" + ipScope.mlirbc.name;
        mlirbc = ipScope.mlirbc;
      };

      rtl = t1Scope.mlirbc-to-sv {
        outputName = "t1-non-testbench-rtl";
        mlirbc = ipScope.lowered-mlirbc;
        mfcArgs = [
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

      omreader = t1Scope.omreader-unwrapped.mkWrapper { mlirbc = ipScope.lowered-mlirbc; };

      # ---------------------------------------------------------------------------------
      # T1 with test bench
      # ---------------------------------------------------------------------------------
      emu-mlirbc = t1Scope.chisel-to-mlirbc {
        outputName = "t1-emu.mlirbc";
        elaboratorArgs = [ "t1emu" "--ip-config" "${elaborateConfigJson}" ];
      };

      lowered-emu-mlirbc = t1Scope.finalize-mlirbc {
        outputName = "lowered" + ipScope.emu-mlirbc.outputName;
        mlirbc = ipScope.emu-mlirbc;
      };

      emu-rtl = t1Scope.mlirbc-to-sv {
        outputName = "t1-emu-rtl";
        mlirbc = ipScope.lowered-emu-mlirbc;
        mfcArgs = [
          "-O=release"
          "--split-verilog"
          "--preserve-values=all"
          "--verification-flavor=if-else-fatal"
          "--lowering-options=verifLabels,omitVersionComment"
          "--strip-debug-info"
        ];
      };

      emu-omreader = t1Scope.omreader-unwrapped.mkWrapper { mlirbc = ipScope.lowered-emu-mlirbc; };
      omGet = args: lib.toLower (lib.fileContents (runCommand "get-${args}" { } ''
        ${ipScope.emu-omreader}/bin/omreader ${args} > $out
      ''));
      rtlDesignMetadata = with ipScope; rec {
        march = omGet "march";
        extensions = builtins.fromJSON (omGet "extensionsJson");
        vlen = omGet "vlen";
        dlen = omGet "dlen";
        xlen = if (lib.hasPrefix "rv32" march) then 32 else 64;
      };

      # ---------------------------------------------------------------------------------
      # VERILATOR
      # ---------------------------------------------------------------------------------
      makeDPI = ipScope.callPackage ../../difftest { };

      verilator-dpi-lib = ipScope.makeDPI {
        outputName = "t1-verilator-dpi-lib";
        emuType = "verilator";
        buildType = "t1";
      };
      verilator-dpi-lib-trace = ipScope.makeDPI {
        outputName = "t1-verilator-trace-dpi-lib";
        emuType = "verilator";
        buildType = "t1";
        enableTrace = true;
      };

      verilator-emu = t1Scope.sv-to-verilator-emulator {
        mainProgram = "t1-verilated-simulator";
        rtl = ipScope.emu-rtl;
        extraVerilatorArgs = [ "${ipScope.verilator-dpi-lib}/lib/libdpi_t1.a" ];
      };
      verilator-emu-trace = t1Scope.sv-to-verilator-emulator {
        mainProgram = "t1-verilated-trace-simulator";
        rtl = ipScope.emu-rtl;
        enableTrace = true;
        extraVerilatorArgs = [ "${ipScope.verilator-dpi-lib-trace}/lib/libdpi_t1.a" ];
      };

      # ---------------------------------------------------------------------------------
      # VCS
      # ---------------------------------------------------------------------------------
      vcs-dpi-lib = ipScope.makeDPI {
        outputName = "t1-vcs-dpi-lib";
        emuType = "vcs";
        buildType = "t1";
      };
      vcs-dpi-lib-trace = ipScope.makeDPI {
        outputName = "t1-vcs-dpi-trace-lib";
        emuType = "vcs";
        enableTrace = true;
        buildType = "t1";
      };

      vcs-emu = t1Scope.sv-to-vcs-simulator {
        mainProgram = "t1-vcs-simulator";
        rtl = ipScope.emu-rtl;
        vcsLinkLibs = [ "${ipScope.vcs-dpi-lib}/lib/libdpi_t1.a" ];
      };
      vcs-emu-trace = t1Scope.sv-to-vcs-simulator {
        mainProgram = "t1-vcs-trace-simulator";
        rtl = ipScope.emu-rtl;
        enableTrace = true;
        vcsLinkLibs = [ "${ipScope.vcs-dpi-lib-trace}/lib/libdpi_t1.a" ];
      };

      offline-checker = ipScope.callPackage ../../difftest/offline-checker.nix { };

      run = ipScope.callPackage ./run { };
    }); # end of ipScope

    subsystem = rec {
      recurseForDerivations = true;

      elaborate = cfgScope.callPackage ./elaborate.nix { target = "subsystem"; /* use-binder = true; */ };
      mlirbc = cfgScope.callPackage ./mlirbc.nix { inherit elaborate; };
      rtl = cfgScope.callPackage ./rtl.nix { inherit mlirbc; };
    };

    release = cfgScope.callPackage ./release { };
  }) # end of cfgScope
  ) # end of anonymous lambda
  allConfigs # end of lib.mapAttrs
