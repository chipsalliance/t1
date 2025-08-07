{
  writeText,
  runCommand,
  simulator,
  jq,
  spike,
  dtc,
  all-tests,
  global-pokedex-config,
}:
let
  difftestConfig = builtins.toJSON {
    elf_path_glob = "${all-tests}/**/*.elf";
    spike = {
      timeout = 30;
      args = [
        "--isa=rv32imac_zvl256b_zve32x_zifencei"
        "--priv=m"
        "--log-commits"
        "-m0x80000000:0x20000000,0x40000000:0x1000"
        "-p1"
        "--hartids=0"
      ];
    };
    pokedex = {
      timeout = 30;
      args = [
        "--config-path"
        "${global-pokedex-config}"
      ];
    };
    mmio_end_addr = "0x40000004";
  };

  configFile = writeText "difftest.json" difftestConfig;
in
runCommand "run-difftest-for-all-cases"
  {
    nativeBuildInputs = [
      simulator
      spike
      jq
      dtc
    ];

    passthru.test-config = configFile;
  }
  ''
    mkdir -p "$out/pass"

    pushd "$out/pass" > /dev/null

    if ! batchrun -c '${configFile}'; then
      failCases=( $(jq -r '.[]' ./batch-run-result.json) )
      for case in "''${failedCases[@]}"; do
        cp "$case".objdump "$out"
        mv "$(basename $case)-pokedex-trace-log.jsonl" "$out"
        mv "$(basename $case)-spike-commits.log" "$out"
        mv "$(basename $case)-difftest-result.json" "$out"
      done
    fi

    popd > /dev/null
  ''
