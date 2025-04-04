{
  linkerScript,
  makeBuilder,
  python3,
  t1main,
}:

let
  builder = makeBuilder { casePrefix = "eval"; };
  build_ntt =
    caseName # must be consistent with attr name
    : main_src: kernel_src: caseArgs: extra_flag:
    builder {
      caseName = caseName;

      src = ./.;

      passthru.featuresRequired = { };

      buildPhase = ''
        runHook preBuild

        ${python3}/bin/python3 ./gen_header.py ${caseArgs}

        $CC -T${linkerScript} \
          -D${caseArgs} \
          ${extra_flag} \
          -I. \
          ${main_src} ${kernel_src} \
          ${t1main} \
          -o $pname.elf

        runHook postBuild
      '';

      meta.description = "test case 'ntt'";
    };

in
{
  ntt_64 = build_ntt "ntt_64" ./ntt.c ./ntt_main.c "ntt_64" "";
  ntt_128 = build_ntt "ntt_128" ./ntt.c ./ntt_main.c "ntt_128" "";
  ntt_256 = build_ntt "ntt_256" ./ntt.c ./ntt_main.c "ntt_256" "";
  ntt_512 = build_ntt "ntt_512" ./ntt.c ./ntt_main.c "ntt_512" "";
  ntt_1024 = build_ntt "ntt_1024" ./ntt.c ./ntt_main.c "ntt_1024" "";
  ntt_4096 = build_ntt "ntt_4096" ./ntt.c ./ntt_main.c "ntt_4096" "";

  ntt_mem_64 = build_ntt "ntt_mem_64" ./ntt_mem.c ./ntt_main.c "ntt_64" "-DUSE_SCALAR";
  ntt_mem_128 = build_ntt "ntt_mem_128" ./ntt_mem.c ./ntt_main.c "ntt_128" "-DUSE_SCALAR";
  ntt_mem_256 = build_ntt "ntt_mem_256" ./ntt_mem.c ./ntt_main.c "ntt_256" "-DUSE_SCALAR";
  ntt_mem_512 = build_ntt "ntt_mem_512" ./ntt_mem.c ./ntt_main.c "ntt_512" "-DUSE_SCALAR";
  ntt_mem_1024 = build_ntt "ntt_mem_1024" ./ntt_mem.c ./ntt_main.c "ntt_1024" "-DUSE_SCALAR";
  ntt_mem_4096 = build_ntt "ntt_mem_4096" ./ntt_mem.c ./ntt_main.c "ntt_4096" "-DUSE_SCALAR";
}
