{ lib
, getTestRequiredFeatures
, linkerScript
, makeBuilder
, findAndBuild
, t1main
}:

let
  builder = makeBuilder { casePrefix = "intrinsic"; };
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

      meta.description = "test case '${caseName}', written in C intrinsic";
    };
in
findAndBuild ./. build

