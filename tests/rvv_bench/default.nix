{ lib
, linkerScript
, makeBuilder
, findAndBuild
, t1main
}:

let
  include = ./_include;
  builder = makeBuilder { casePrefix = "rvv_bench"; };
  build = { caseName, sourcePath }:
    builder {
      inherit caseName;

      src = sourcePath;

      isFp = lib.pathExists (lib.path.append sourcePath "isFp");

      buildPhase = ''
        runHook preBuild

        $CC -E -DINC=$PWD/${caseName}.S -E ${include}/template.S -o functions.S
        $CC -I${include} ${caseName}.c -T${linkerScript} ${t1main} functions.S -o $pname.elf

        runHook postBuild
      '';

      meta.description = "test case '${caseName}', written in C intrinsic";
    };
in
  findAndBuild ./. build

