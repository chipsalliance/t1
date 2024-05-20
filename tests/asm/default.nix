{ lib
, linkerScript
, makeBuilder
, findAndBuild
, t1main
}:

let
  builder = makeBuilder { casePrefix = "asm"; };
  build = { caseName, sourcePath }:
    builder {
      inherit caseName;

      src = sourcePath;

      isFp = lib.pathExists (lib.path.append sourcePath "isFp");

      buildPhase = ''
        runHook preBuild

        $CC -T${linkerScript} \
          ${caseName}.S \
          ${t1main} \
          -o $pname.elf

        runHook postBuild
      '';

      meta.description = "test case '${caseName}', written in C assembly";
    };
in
  findAndBuild ./. build

