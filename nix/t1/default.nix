{ lib
, stdenv
, useMoldLinker
, newScope
, runCommand
}:

let
  moldStdenv = useMoldLinker stdenv;

  t1ConfigsDir = ../../configgen/generated;

  # allConfigs is a (configName -> configJsonPath) map
  allConfigs = lib.mapAttrs'
    (fileName: fileType:
      assert fileType == "regular" && lib.hasSuffix ".json" fileName;
      lib.nameValuePair
        (lib.removeSuffix ".json" fileName)
        (lib.path.append t1ConfigsDir fileName))
    (builtins.readDir t1ConfigsDir);
in
lib.makeScope newScope
  (t1Scope:
  {
    inherit allConfigs;
    recurseForDerivations = true;

    dependencies = t1Scope.callPackage ./dependencies { };

    # ---------------------------------------------------------------------------------
    # Compile the T1 mill modules into one big derivation and split them to different derivation
    # ---------------------------------------------------------------------------------
    _t1MillModules = t1Scope.callPackage ./mill-modules.nix { };
    elaborator = t1Scope._t1MillModules.elaborator // { meta.mainProgram = "elaborator"; };
    configgen = t1Scope._t1MillModules.configgen // { meta.mainProgram = "configgen"; };
    t1package = t1Scope._t1MillModules.t1package;

    # ---------------------------------------------------------------------------------
    # Lowering utilities
    # ---------------------------------------------------------------------------------

    # chisel-to-mlirbc :: { outputName :: String, elaboratorArgs :: List<String> } -> Derivation
    #
    # chisel-to-mlirbc accept outputName as output mlirbc file name, and elaboratorArgs for elaborator to run.
    # Return a derivation with the parsed mlirbc file as output.
    chisel-to-mlirbc = t1Scope.callPackage ./conversion/chisel-to-mlirbc.nix { };

    # finalize-mlirbc :: { outputName :: String, mlirbc :: Derivation } -> Derivation
    #
    # finalize-mlirbc will run actual firtool lowering to the MLIRBC file
    #
    # Default using below lowering command line arguments.
    # Can be override with optional argument `loweringArgs`.
    #
    #  * --emit-bytecode
    #  * -O=debug
    #  * --preserve-values=named
    #  * --lowering-options=verifLabels
    finalize-mlirbc = t1Scope.callPackage ./conversion/finalize-mlirbc.nix { };

    # mlirbc-to-sv :: { outputName :: String, mlirbc :: Derivation, mfcArgs :: List<String> } -> Derivation
    #
    # mlirbc-to-sv will convert pass-in mlirbc to system verilog, using the circt toolchains.
    # Returning derivation contains system verilog and a filelist.f demostrate the verilog hierarchy.
    mlirbc-to-sv = t1Scope.callPackage ./conversion/mlirbc-to-sv.nix { };

    # sv-to-verilated-lib :: { mainProgram :: String, rtl :: Derivation, enableTrace :: Bool, extraVerilatorArgs :: List<String> } -> Derivation
    #
    # sv-to-verilated-lib use verilator to codegen C and link them with dpi library to generate the final emulator.
    # Default using 8 thread for verilator.
    #
    # This function also accept the below arguments to override the default:
    #
    # * verilatorFilelist: filename of the filelist.f file, default using "filelist.f"
    # * verilatorTop: Top module of the system verilog, default using "TestBench"
    # * verilatorThreads: Threads for final verilating, default using 8
    # * verilatorArgs: Final arguments that pass to the verilator.
    sv-to-verilator-emulator = t1Scope.callPackage ./conversion/sv-to-verilator-emulator.nix { stdenv = moldStdenv; };

    # sv-to-vcs-simulator :: { mainProgram :: String, rtl :: Derivation, enableTrace :: Bool, vcsLinkLibs :: List<String> } -> Derivation
    #
    # sv-to-vcs-simulator will compile the given rtl, link with path specified in vcsLinksLibs to produce a VCS emulator.
    # enableTrace is false by default;
    sv-to-vcs-simulator = t1Scope.callPackage ./conversion/sv-to-vcs-simulator.nix { };

    # ---------------------------------------------------------------------------------
    # Grouped emulator packages
    # ---------------------------------------------------------------------------------
    # Nix specification for t1rocket (with rocket-chip as Scalar core) emulator
    t1rocket = t1Scope.callPackage ./t1rocket.nix { };
    # Nix specification for rocket (rocket-chip only) emulator
    rocketv = t1Scope.callPackage ../../rocketemu { };

    omreader-unwrapped = t1Scope.callPackage ./omreader.nix { };
  }
    # Nix specification for t1 (with spike only) emulator
    # We don't expect extra scope for t1 stuff, so here we merge the t1 at t1Scope level.
    # Note: Don't use callPackage here, or t1Scope will fall into recursive search.
    // ((import ./t1.nix) { inherit lib allConfigs t1Scope runCommand; })
  )
