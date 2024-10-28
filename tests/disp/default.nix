{ lib
, linkerScript
, makeBuilder
, findAndBuild
, t1main
, getTestRequiredFeatures
}:

let
  builder = makeBuilder { casePrefix = "disp"; };
  build = { caseName, sourcePath }:
    builder {
      inherit caseName;

      src = sourcePath;

      passthru.featuresRequired = getTestRequiredFeatures sourcePath;

      buildPhase = ''
        runHook preBuild

        $CC -T${linkerScript} \
          ${caseName}.c \
          ${t1main} \
          -o $pname.elf

        runHook postBuild
      '';

      meta.description = "test case '${caseName}' for display devices, written in C";
    };
in
findAndBuild ./. build

