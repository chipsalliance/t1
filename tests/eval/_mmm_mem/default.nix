{ lib
, linkerScript
, makeBuilder
, t1main
}:

let
  builder = makeBuilder { casePrefix = "eval"; };
  build_mmm = caseName /* must be consistent with attr name */: bn: vl:
    builder {
      caseName = caseName;

      src = ./.;

      passthru.featuresRequired = { };

      buildPhase = ''
        runHook preBuild

        $CC -T${linkerScript} -DLEN=${toString bn} \
          ${./mmm_main.c} ./mmm_${toString bn}_vl${toString vl}.S \
          ${t1main} \
          -o $pname.elf

        runHook postBuild
      '';

      meta.description = "test case 'ntt'";
    };
  configs = lib.cartesianProduct { bn = [ 256 512 ]; vl = [ 128 256 4096 ]; };
in
builtins.listToAttrs (builtins.map
  (
    { bn, vl }:
    let name = "mmm_mem_${toString bn}_vl${toString vl}"; in
    lib.nameValuePair
      name
      (build_mmm name bn vl)
  )
  configs)
