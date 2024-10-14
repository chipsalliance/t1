{ lib, allConfigs, t1Scope, runCommand }:

# return attribute set with following hierarchy:
# {
#   "blastoise": { generatorName = { mlirbc = ...; vcs-emu = ...; ... } }
#   "...": { generatorName = { mlirbc = ...; vcs-emu = ...; ... } }
# }
lib.mapAttrs
  (configName: allGenerators:
  let
    strippedGeneratorData = lib.mapAttrs'
      (fullClassName: origData:
        lib.nameValuePair
          (lib.head
            (lib.splitString "."
              (lib.removePrefix "org.chipsalliance.t1.elaborator." fullClassName)))
          (origData // { inherit fullClassName; }))
      allGenerators;
  in
  lib.mapAttrs
    (topName: generator:
    lib.makeScope t1Scope.newScope
      (mostInnerScope:
      lib.recurseIntoAttrs {
        inherit configName topName;

        cases = mostInnerScope.callPackage ../../tests { };

        mlirbc = t1Scope.chisel-to-mlirbc {
          outputName = "${generator.fullClassName}.mlirbc";
          generatorClassName = generator.fullClassName;
          elaboratorArgs = "config ${generator.cmdopt}";
        };

        lowered-mlirbc = t1Scope.finalize-mlirbc {
          outputName = "lowered-" + mostInnerScope.mlirbc.name;
          mlirbc = mostInnerScope.mlirbc;
        };

        rtl = t1Scope.mlirbc-to-sv {
          outputName = "${generator.fullClassName}-rtl";
          mlirbc = mostInnerScope.lowered-mlirbc;
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

        omGet = args: lib.toLower (lib.fileContents (runCommand "get-${args}" { } ''
          ${t1Scope.omreader-unwrapped}/bin/omreader \
            ${lib.replaceStrings ["elaborator"] ["omreader"] generator.fullClassName} \
            ${args} \
            --mlirbc-file ${mostInnerScope.lowered-mlirbc}/${mostInnerScope.lowered-mlirbc.name} \
            > $out
        ''));
        rtlDesignMetadata = with mostInnerScope; rec {
          march = omGet "march";
          extensions = builtins.fromJSON (omGet "extensionsJson");
          vlen = omGet "vlen";
          dlen = omGet "dlen";
          xlen = if (lib.hasPrefix "rv32" march) then 32 else 64;
        };

        # ---------------------------------------------------------------------------------
        # VERILATOR
        # ---------------------------------------------------------------------------------
        makeDifftest = mostInnerScope.callPackage ../../difftest { };

        verilator-dpi-lib = mostInnerScope.makeDifftest {
          outputName = "${topName}-verilator-dpi-lib";
          emuType = "verilator";
          moduleType = "dpi_${topName}";
        };
        verilator-dpi-lib-trace = mostInnerScope.makeDifftest {
          outputName = "${topName}-verilator-trace-dpi-lib";
          emuType = "verilator";
          moduleType = "dpi_${topName}";
          enableTrace = true;
        };

        verilator-emu = t1Scope.sv-to-verilator-emulator {
          mainProgram = "${topName}-verilated-simulator";
          rtl = mostInnerScope.rtl;
          extraVerilatorArgs = [ "${mostInnerScope.verilator-dpi-lib}/lib/libdpi_${topName}.a" ];
        };
        verilator-emu-trace = t1Scope.sv-to-verilator-emulator {
          mainProgram = "${topName}-verilated-trace-simulator";
          rtl = mostInnerScope.rtl;
          enableTrace = true;
          extraVerilatorArgs = [ "${mostInnerScope.verilator-dpi-lib-trace}/lib/libdpi_${topName}.a" ];
        };

        # ---------------------------------------------------------------------------------
        # VCS
        # ---------------------------------------------------------------------------------
        vcs-dpi-lib = mostInnerScope.makeDifftest {
          outputName = "${topName}-vcs-dpi-lib";
          emuType = "vcs";
          moduleType = "dpi_${topName}";
        };
        vcs-dpi-lib-trace = mostInnerScope.makeDifftest {
          outputName = "${topName}-vcs-dpi-trace-lib";
          emuType = "vcs";
          enableTrace = true;
          moduleType = "dpi_${topName}";
        };

        offline-checker = mostInnerScope.makeDifftest {
          outputName = "${topName}-offline-checker";
          moduleType = "offline_${topName}";
        };

        vcs-emu = t1Scope.sv-to-vcs-simulator {
          mainProgram = "${topName}-vcs-simulator";
          rtl = mostInnerScope.rtl;
          vcsLinkLibs = [ "${mostInnerScope.vcs-dpi-lib}/lib/libdpi_${topName}.a" ];
        };
        vcs-emu-trace = t1Scope.sv-to-vcs-simulator {
          mainProgram = "${topName}-vcs-trace-simulator";
          rtl = mostInnerScope.rtl;
          enableTrace = true;
          vcsLinkLibs = [ "${mostInnerScope.vcs-dpi-lib-trace}/lib/libdpi_${topName}.a" ];
        };

        run = mostInnerScope.callPackage ./run { };
      })
    )
    strippedGeneratorData
  ) # end of anonymous lambda
  allConfigs # end of lib.mapAttrs
