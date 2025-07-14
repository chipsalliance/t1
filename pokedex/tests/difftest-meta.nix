{
  writeText,
  runCommand,
  simulator,
  jq,
  spike,
  dtc,
  all-tests,
}:
let
  configuration = {
    elf_path_glob = "${all-tests}/**/*.elf";
    spike_args = [
      "--isa=rv32i"
      "--priv=m"
      "--log-commits"
      "-m0x80000000:0x20000000,0x40000000:0x20000000"
    ];
    pokedex_args = [
      "--memory-size"
      "0xc0000000"
      "--max-same-instruction"
      "20"
    ];
    end_pattern = {
      action = "write";
      memory_address = "0x40000000";
      data = "0x1";
    };
  };
  configFile = writeText "difftest.json" (builtins.toJSON configuration);
in
runCommand "run-difftest-for-all-cases"
  {
    nativeBuildInputs = [
      simulator
      spike
      jq
      dtc
    ];
  }
  ''
    mkdir -p "$out"

    if difftest --config-path '${configFile}'; then
      jq -n '.success = true' > "$out/meta.json"
    else
      jq -n '.success = false' > "$out/meta.json"

      find '${all-tests}' -name '*.objdump' -type f -exec cp '{}' "$out/" ';'
      find . -name '*-pokedex-sim-event.jsonl' -type f -exec cp '{}' "$out/" ';'
      find . -name '*-spike-commits.log' -type f -exec cp '{}' "$out/" ';'
    fi
  ''
