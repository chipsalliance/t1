{ lib
, getTestRequiredFeatures
, linkerScript
, makeBuilder
, findAndBuild
, t1main
, makeEmuResult
}:

let
  include = ./_include;
  builder = makeBuilder { casePrefix = "rvv_bench"; };
  build = { caseName, sourcePath }:
    let
      drv = builder
        {
          inherit caseName;

          src = sourcePath;

          featuresRequired = getTestRequiredFeatures sourcePath;

          buildPhase = ''
            runHook preBuild

            $CC -E -DINC=$PWD/${caseName}.S -E ${include}/template.S -o functions.S
            $CC -I${include} ${caseName}.c -T${linkerScript} ${t1main} functions.S -o $pname.elf

            runHook postBuild
          '';

          meta.description = "test case '${caseName}', written in C intrinsic";

          passthru.emu-result = makeEmuResult drv;
        };
    in
    drv;
in
findAndBuild ./. build

