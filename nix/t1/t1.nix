{
  lib,
  allConfigs,
  t1Scope,
  runCommand,
  runtimeShell,
  jq,
}:

# return attribute set with following hierarchy:
# {
#   "blastoise": { generatorName = { mlirbc = ...; vcs-emu = ...; ... } }
#   "...": { generatorName = { mlirbc = ...; vcs-emu = ...; ... } }
# }
let
  forEachConfig =
    attrBuilder:
    lib.mapAttrs (configName: allGenerators: attrBuilder configName allGenerators) allConfigs;
in
forEachConfig (
  configName: allGenerators:
  let
    strippedGeneratorData = lib.mapAttrs' (
      fullClassName: origData:
      lib.nameValuePair (lib.head (
        lib.splitString "." (lib.removePrefix "org.chipsalliance.t1.elaborator." fullClassName)
      )) (origData // { inherit fullClassName; })
    ) allGenerators;

    # forEachTop accept a function that takes three parameter and return an
    # attribute set as packages set. It will pass the RTL top name like
    # "t1rocketemu", "t1emu" of type "string" as the first parameter, an
    # attribute set that contains elaborator full class name
    # (`generator.fullClassName`) and elaborate argument (`generator.cmdopt`)
    # as the second parameter. An inner scope reference attribute as the third
    # parameter.
    forEachTop =
      scopeBuilderFn:
      lib.mapAttrs (
        topName: generatorData:
        lib.makeScope t1Scope.newScope (
          scope: lib.recurseIntoAttrs (scopeBuilderFn topName generatorData scope)
        )
      ) strippedGeneratorData;
  in
  forEachTop (
    topName: generator: self: rec {
      inherit configName topName;

      cases = self.callPackage ../../tests { };

      chisel-mlirbc = t1Scope.chisel-to-mlirbc {
        outputName = "${generator.fullClassName}.mlirbc";
        generatorClassName = generator.fullClassName;
        elaboratorArgs = "config ${generator.cmdopt}";
      };

      # List of zaozi modules to elaborate and link.
      # Each entry: { className, paramJsonName }
      # paramJsonName matches the filename dumped by the ExtModule constructor into zaozi-params/
      zaozi-modules = lib.optionals (lib.hasInfix "rv_xsfmm" generator.cmdopt) [
        {
          className = "org.chipsalliance.t1.rtl.zvma.ZVMAProcessingElement";
          paramJsonName = "ZVMAProcessingElement.json";
        }
      ];

      zaozi-mlirbcs = map (
        mod:
        t1Scope.zaozi-to-mlirbc {
          outputName = "${lib.last (lib.splitString "." mod.className)}.mlirbc";
          generatorClassName = mod.className;
          parameterJson = "${chisel-mlirbc}/zaozi-params/${mod.paramJsonName}";
        }
      ) zaozi-modules;

      mlirbc =
        if zaozi-modules != [ ] then
          t1Scope.firld-link {
            outputName = "${generator.fullClassName}.mlirbc";
            mlirbcs = [ chisel-mlirbc ] ++ zaozi-mlirbcs;
            baseCircuit = lib.last (lib.splitString "." generator.fullClassName);
          }
        else
          chisel-mlirbc;

      lowered-mlirbc = t1Scope.finalize-mlirbc {
        outputName = "lowered-" + self.mlirbc.name;
        mlirbc = self.mlirbc;
      };

      rtl = t1Scope.mlirbc-to-sv {
        outputName = "${generator.fullClassName}-rtl";
        mlirbc = self.lowered-mlirbc;
        mfcArgs = [
          "-O=release"
          "--disable-all-randomization"
          "--split-verilog"
          "--preserve-values=all"
          "--strip-debug-info"
          "--strip-fir-debug-info"
          "--verification-flavor=sva"
          "--lowering-options=verifLabels,omitVersionComment,emittedLineLength=240,locationInfoStyle=none,disallowLocalVariables"
        ];
        enableLayers = [
          "verification"
          "verification.assert"
          "verification.assume"
          "verification.cover"
        ];
      };

      omreader =
        runCommand "wrap-omreader"
          {
            meta.mainProgram = "omreader";
          }
          ''
            mkdir -p $out/bin
            tee -a $out/bin/omreader <<EOF
            #!${runtimeShell}
            exec ${t1Scope.omreader-unwrapped}/bin/omreader \
              ${lib.replaceStrings [ "elaborator" ] [ "omreader" ] generator.fullClassName} \
              --mlirbc-file ${self.lowered-mlirbc}/${self.lowered-mlirbc.name} \
              $@
            EOF

            chmod +x $out/bin/omreader
          '';
      rtlDesignMetadataJson =
        runCommand "get-rtl-design-metadata-from-om"
          {
            nativeBuildInputs = [
              jq
              self.omreader
            ];
          }
          ''
            omreader | jq '{march, extensions, vlen, dlen, xlen: (if (.march | startswith("rv32")) then 32 else 64 end)}' >$out
          '';
      rtlDesignMetadata = with builtins; fromJSON (readFile self.rtlDesignMetadataJson);

      # ---------------------------------------------------------------------------------
      # VERILATOR
      # ---------------------------------------------------------------------------------
      makeDifftest = lib.makeOverridable (self.callPackage ../../difftest { });

      # Here we read all files under ../../${topName}/vsrc, and create a new nix
      # store root with only files under the vsrc directory, then convert it
      # into a list of file. This is to avoid any source changes in t1 source
      # root causing the emulator to rebuild. Notes that the `topName`
      # variable will be like t1emu or t1rocketemu.
      clean-vsrc =
        with lib.fileset;
        toSource {
          root = ../../${topName}/vsrc;
          fileset = unions (toList ../../${topName}/vsrc);
        };

      # Workaround: zaozi VerilogWrapper references external SRAM modules
      # that don't have Verilog implementations yet. Hand-written .sv files
      # under t1zaozi/vsrc/ provide temporary implementations.
      clean-zaozi-vsrc =
        with lib.fileset;
        toSource {
          root = ../../t1zaozi/vsrc;
          fileset = unions (toList ../../t1zaozi/vsrc);
        };

      verilator-dpi-lib = self.makeDifftest {
        outputName = "${topName}-verilator-dpi-lib";
        emuType = "verilator";
        moduleType = "dpi_${topName}";
      };

      verilator-emu = t1Scope.sv-to-verilator-emulator {
        mainProgram = "${topName}-verilated-simulator";
        topModule = "TestBench";
        rtl = self.rtl;
        vsrc =
          lib.filesystem.listFilesRecursive self.clean-vsrc.outPath
          ++ lib.optionals (zaozi-modules != [ ]) (
            lib.filesystem.listFilesRecursive self.clean-zaozi-vsrc.outPath
          );
        dpiLibs = [ "${self.verilator-dpi-lib}/lib/libdpi_${topName}.a" ];
      };
      verilator-emu-trace = self.verilator-emu.override {
        enableTrace = true;
        mainProgram = "${topName}-verilated-trace-simulator";
      };

      # ---------------------------------------------------------------------------------
      # VCS
      # ---------------------------------------------------------------------------------
      vcs-dpi-lib = self.makeDifftest {
        outputName = "${topName}-vcs-dpi-lib";
        emuType = "vcs";
        moduleType = "dpi_${topName}";
      };

      inherit (t1Scope) sim-checker;

      # We do not use vcs-emu-static every day,
      # but we may switch back to static once rtLink breaks
      vcs-emu-static = self.vcs-emu.override {
        mainProgram = "${topName}-vcs-simulator-static";
        vcsLinkLibs = [ "${self.vcs-dpi-lib}/lib/libdpi_${topName}.a" ];
        rtLinkDpiLib = null;
      };

      vcs-emu = t1Scope.sv-to-vcs-simulator {
        mainProgram = "${topName}-vcs-simulator";
        topModule = "TestBench";
        rtl = self.rtl;
        vsrc =
          lib.filesystem.listFilesRecursive self.clean-vsrc.outPath
          ++ lib.optionals (zaozi-modules != [ ]) (
            lib.filesystem.listFilesRecursive self.clean-zaozi-vsrc.outPath
          );
        rtLinkDpiLib = self.vcs-dpi-lib;
      };
      vcs-emu-cover = self.vcs-emu.override {
        enableCover = true;
        mainProgram = "${topName}-vcs-cover-simulator";
      };
      vcs-emu-trace = self.vcs-emu.override {
        enableTrace = true;
        mainProgram = "${topName}-vcs-trace-simulator";
      };

      run = self.callPackage ./run { };

      docker-image = self.callPackage ./release/docker-image.nix { };
    }
  ) # end of forEachTop
)
