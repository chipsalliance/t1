{ linkerScript
, makeBuilder
, t1main
}:

let
  builder = makeBuilder { casePrefix = "eval"; };
  build_ntt = caseName /* must be consistent with attr name */ : len: kernel_src:
    builder {
      caseName = caseName;

      src = ./.;

      passthru.featuresRequired = { };

      buildPhase = ''
        runHook preBuild

        $CC -T${linkerScript} -DLEN=${toString len} \
          ${./mmm_main.c} ${kernel_src} \
          ${t1main} \
          -o $pname.elf

        runHook postBuild
      '';

      meta.description = "test case 'ntt'";
    };

in {
  mmm_mem_512_vl4096 = build_ntt "mmm_mem_512_vl4096" 4096 ./mmm_512_vl4096.S;
  mmm_mem_256_vl4096 = build_ntt "mmm_mem_256_vl4096" 4096 ./mmm_256_vl4096.S;
}
