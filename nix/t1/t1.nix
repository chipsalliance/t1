{ lib, allConfigs, t1Scope, runCommand, runtimeShell, jq }:

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
      (innerMostScope:
      lib.recurseIntoAttrs {
        inherit configName topName;

        cases = innerMostScope.callPackage ../../tests { };

        mlirbc = t1Scope.chisel-to-mlirbc {
          outputName = "${generator.fullClassName}.mlirbc";
          generatorClassName = generator.fullClassName;
          elaboratorArgs = "config ${generator.cmdopt}";
        };

        lowered-mlirbc = t1Scope.finalize-mlirbc {
          outputName = "lowered-" + innerMostScope.mlirbc.name;
          mlirbc = innerMostScope.mlirbc;
        };

        rtl = t1Scope.mlirbc-to-sv {
          outputName = "${generator.fullClassName}-rtl";
          mlirbc = innerMostScope.lowered-mlirbc;
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

        omreader = runCommand "wrap-omreader" { } ''
          mkdir -p $out/bin
          tee -a $out/bin/omreader <<EOF
          #!${runtimeShell}
          cmd=\$1; shift
          [[ -z "\$cmd" ]] && echo "missing argument" && exit 1

          exec ${t1Scope.omreader-unwrapped}/bin/omreader \
            ${lib.replaceStrings ["elaborator"] ["omreader"] generator.fullClassName} \
            \$cmd \
            --mlirbc-file ${innerMostScope.lowered-mlirbc}/${innerMostScope.lowered-mlirbc.name} \
            $@
          EOF

          chmod +x $out/bin/omreader
        '';
        rtlDesignMetadataJson = runCommand "get-rtl-design-metadata-from-om" { nativeBuildInputs = [ jq innerMostScope.omreader ]; } ''
          march=$(omreader march)
          extensions=$(omreader extensions)
          vlen=$(omreader vlen)
          dlen=$(omreader dlen)

          jq --null-input \
            --arg march $march \
            --arg extensions $extensions \
            --arg vlen $vlen \
            --arg dlen $dlen \
            '{ "march": $march,
               "extensions": $extensions|split("_"),
               "vlen": $vlen|tonumber,
               "dlen": $dlen|tonumber,
               "xlen": (if ($march|startswith("rv32")) then 32 else 64 end) }' \
            > $out
        '';
        rtlDesignMetadata = with builtins; fromJSON (readFile innerMostScope.rtlDesignMetadataJson);

        # ---------------------------------------------------------------------------------
        # VERILATOR
        # ---------------------------------------------------------------------------------
        makeDifftest = innerMostScope.callPackage ../../difftest { };

        verilator-dpi-lib = innerMostScope.makeDifftest {
          outputName = "${topName}-verilator-dpi-lib";
          emuType = "verilator";
          moduleType = "dpi_${topName}";
        };
        verilator-dpi-lib-trace = innerMostScope.makeDifftest {
          outputName = "${topName}-verilator-trace-dpi-lib";
          emuType = "verilator";
          moduleType = "dpi_${topName}";
          enableTrace = true;
        };

        verilator-emu = t1Scope.sv-to-verilator-emulator {
          mainProgram = "${topName}-verilated-simulator";
          rtl = innerMostScope.rtl;
          extraVerilatorArgs = [ "${innerMostScope.verilator-dpi-lib}/lib/libdpi_${topName}.a" ];
        };
        verilator-emu-trace = t1Scope.sv-to-verilator-emulator {
          mainProgram = "${topName}-verilated-trace-simulator";
          rtl = innerMostScope.rtl;
          enableTrace = true;
          extraVerilatorArgs = [ "${innerMostScope.verilator-dpi-lib-trace}/lib/libdpi_${topName}.a" ];
        };

        # ---------------------------------------------------------------------------------
        # VCS
        # ---------------------------------------------------------------------------------
        vcs-dpi-lib = innerMostScope.makeDifftest {
          outputName = "${topName}-vcs-dpi-lib";
          emuType = "vcs";
          moduleType = "dpi_${topName}";
        };
        vcs-dpi-lib-trace = innerMostScope.makeDifftest {
          outputName = "${topName}-vcs-dpi-trace-lib";
          emuType = "vcs";
          enableTrace = true;
          moduleType = "dpi_${topName}";
        };

        offline-checker = innerMostScope.makeDifftest {
          outputName = "${topName}-offline-checker";
          moduleType = "offline_${topName}";
        };

        vcs-emu = t1Scope.sv-to-vcs-simulator {
          mainProgram = "${topName}-vcs-simulator";
          rtl = innerMostScope.rtl;
          vcsLinkLibs = [ "${innerMostScope.vcs-dpi-lib}/lib/libdpi_${topName}.a" ];
        };
        vcs-emu-trace = t1Scope.sv-to-vcs-simulator {
          mainProgram = "${topName}-vcs-trace-simulator";
          rtl = innerMostScope.rtl;
          enableTrace = true;
          vcsLinkLibs = [ "${innerMostScope.vcs-dpi-lib-trace}/lib/libdpi_${topName}.a" ];
        };

        run = innerMostScope.callPackage ./run { };
      })
    )
    strippedGeneratorData
  ) # end of anonymous lambda
  allConfigs # end of lib.mapAttrs
