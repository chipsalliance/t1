{
  lib,
  linkerScript,
  riscv-vector-test,
  makeBuilder,
  # Instead of testing feature is supported on TOP level,
  # codegen case are always generated with supported code.
  rtlDesignMetadata,
}:

let
  builder = makeBuilder { casePrefix = "codegen"; };
  makeCaseName = lib.replaceStrings [ "." ] [ "_" ];

  build =
    { rawCaseName, extra }:
    builder rec {
      caseName = makeCaseName rawCaseName;

      includeArgs = [
        "-I${./include}"
        "-I${riscv-vector-test}/include"
      ];

      dontUnpack = true;

      buildPhase = ''
        runHook preBuild

        # Golang only accept "-flag=value" pattern to set value for flag, don't mess around with other cmd line option.
        ${riscv-vector-test}/bin/single \
          -VLEN "${builtins.toString rtlDesignMetadata.vlen}" \
          -XLEN "${builtins.toString rtlDesignMetadata.xlen}" \
          -float16=false \
          -repeat 16 \
          -testfloat3level 2 \
          -configfile ${riscv-vector-test}/configs/${rawCaseName}.toml \
          -outputfile $pname.S

        $CC $pname.S -T ${linkerScript} $includeArgs -o $pname.elf
        echo "Complilation done"

        echo "+assert ${caseName}" > $pname.cover

        runHook postBuild
      '';

      meta.description = "test case '${caseName}' generated by codegen";
    }
    // extra;

  buildTestsFromFile =
    file: extra:
    with lib;
    let
      rawCaseNames = lib.splitString "\n" (lib.fileContents file);
    in
    (listToAttrs (
      map (
        rawCaseName:
        nameValuePair (makeCaseName rawCaseName) (build {
          inherit rawCaseName extra;
        })
      ) rawCaseNames
    ));

  commonTests = buildTestsFromFile ./common.txt {
    passthru.featuresRequired = {
      extensions = [ ];
    };
  };
  fpTests = buildTestsFromFile ./fp.txt {
    passthru.featuresRequired = {
      extensions = [ "zve32f" ];
    };
  };
  zvbbTests = buildTestsFromFile ./zvbb.txt {
    passthru.featuresRequired = {
      extensions = [ "zvbb" ];
    };
  };
in
lib.recurseIntoAttrs (
  commonTests
  // lib.optionalAttrs (lib.elem "zve32f" rtlDesignMetadata.extensions) fpTests
  // lib.optionalAttrs (lib.elem "zvbb" rtlDesignMetadata.extensions) zvbbTests
)
