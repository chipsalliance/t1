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
  configFile = writeText "difftest.json" ''
    elf-path-glob "${all-tests}/smoke/*.elf"

    spike {
      timeout 30
      cli-args "--isa=rv32imc_zvl256b_zve32x_zifencei" \
          "--priv=m" "--log-commits" \
          "-m0x80000000:0x20000000,0x40000000:0x1000"
    }

    pokedex {
      timeout 30
      cli-args "--dts-cfg-path" "${global-pokedex-config}"
    }

    mmio-end-addr "0x40000004"
  '';
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

    pushd "$out/pass"

    if ! batchrun --config-path '${configFile}'; then
      failCases=( $(jq -r '.[]' ./batch-run-result.json) )
      for case in "''${failedCases[@]}"; do
        cp "$case".objdump "$out"
        mv "$(basename $case)-pokedex-trace-log.jsonl" "$out"
        mv "$(basename $case)-spike-commits.log" "$out"
        mv "$(basename $case)-difftest-result.json" "$out"
      done
    fi

    popd
  ''
