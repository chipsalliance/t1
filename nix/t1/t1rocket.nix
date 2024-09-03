{ lib
, newScope

, chisel-to-mlirbc
, finalize-mlirbc
, mlirbc-to-sv
, sv-to-verilator-emulator
, sv-to-vcs-simulator
}:

{
  ip = lib.makeScope newScope (scope: {

    cases = scope.callPackage ../../tests { };
    run = scope.callPackage ./run { configName = "t1rocket"; };

    mlirbc = chisel-to-mlirbc {
      outputName = "t1rocketemu-parsed.mlirbc";
      elaboratorArgs = [
        "t1rocketemu"
        "--t1rocket-config"
        "${../../t1rocketemu/configs/default.json}"
      ];
    };

    lowered-mlirbc = finalize-mlirbc {
      outputName = "t1rocketemu-lowered.mlirbc";
      mlirbc = scope.mlirbc;
    };

    rtl = mlirbc-to-sv {
      outputName = "t1rocketemu-rtl";
      mlirbc = scope.lowered-mlirbc;
      mfcArgs = [
        "-O=release"
        "--split-verilog"
        "--preserve-values=all"
        "--verification-flavor=if-else-fatal"
        "--lowering-options=verifLabels,omitVersionComment"
        "--strip-debug-info"
      ];
    };

    makeDPI = scope.callPackage ../../difftest { };
    verilator-dpi-lib = scope.makeDPI {
      outputName = "t1rocket-verilator-dpi-lib";
      buildType = "t1rocket";
    };
    verilator-dpi-lib-trace = scope.makeDPI {
      outputName = "t1rocket-verilator-trace-dpi-lib";
      buildType = "t1rocket";
      enableTrace = true;
    };

    verilator-emu = sv-to-verilator-emulator {
      mainProgram = "t1rocket-verilated-simulator";
      rtl = scope.rtl;
      extraVerilatorArgs = [
        "--threads-max-mtasks"
        "8000"
        "${scope.verilator-dpi-lib}/lib/libdpi_t1rocket.a"
      ];
    };
    verilator-emu-trace = sv-to-verilator-emulator {
      mainProgram = "t1rocket-verilated-trace-simulator";
      rtl = scope.rtl;
      enableTrace = true;
      extraVerilatorArgs = [
        "--threads-max-mtasks"
        "8000"
        "${scope.verilator-dpi-lib}/lib/libdpi_t1rocket.a"
      ];
    };

    offline-checker = scope.callPackage ../../t1rocketemu/offline { };

    vcs-dpi-lib = scope.makeDPI {
      outputName = "t1rocket-vcs-dpi-lib";
      buildType = "t1rocket";
      emuType = "vcs";
    };
    vcs-dpi-lib-trace = scope.makeDPI {
      outputName = "t1rocket-vcs-dpi-trace-lib";
      buildType = "t1rocket";
      emuType = "vcs";
      enableTrace = true;
    };

    vcs-emu = sv-to-vcs-simulator {
      mainProgram = "t1rocket-vcs-simulator";
      rtl = scope.rtl;
      vcsLinkLibs = [ "${scope.vcs-dpi-lib}/lib/libdpi_t1rocket.a" ];
    };
    vcs-emu-trace = sv-to-vcs-simulator {
      mainProgram = "t1rocket-vcs-trace-simulator";
      rtl = scope.rtl;
      enableTrace = true;
      vcsLinkLibs = [ "${scope.vcs-dpi-lib-trace}/lib/libdpi_t1rocket.a" ];
    };

    getVLen = ext:
      let
        val = builtins.tryEval
          (lib.toInt
            (lib.removeSuffix "b"
              (lib.removePrefix "zvl"
                (lib.toLower ext))));
      in
      if val.success then
        val.value
      else
        throw "Invalid vlen extension `${ext}` specify, expect Zvl{N}b";

    # TODO: designConfig should be read from OM
    designConfig = with builtins; (fromJSON (readFile ../../t1rocketemu/configs/default.json)).parameter;

    # TODO: We should have a type define, to keep t1 and t1rocket feeds same `rtlDesignMetadata` data structure.
    rtlDesignMetadata = rec {
      # TODO: `march` and `dlen` should be read from OM
      #
      # Although the string is already hard-coded in lower case, the toLower function call here is to remind developer that,
      # when we switch OM, we should always ensure the march input is lower case.
      march = lib.toLower "rv32gc_zve32f_zvl1024b";
      extensions = lib.splitString "_" march;
      dlen = scope.designConfig.dLen;
      xlen = if (lib.hasPrefix "rv32" march) then 32 else 64;

      # Find "Zvl{N}b" string in march and parse it to vlen.
      # Extract earlier so that downstream derivation that relies on this value doesn't have to parse the string multiple times.
      vlen = lib.pipe (march) [
        (lib.splitString "_")
        (lib.filter (x: lib.hasPrefix "zvl" x))
        (lib.last)
        (lib.removePrefix "zvl")
        (lib.removeSuffix "b")
        (lib.toInt)
      ];
    };
  });
}
