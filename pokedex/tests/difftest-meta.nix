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
  configuration = {
    elf_path_glob = "${all-tests}/**/*.elf";
    spike_args = [
      "--isa=rv32im_zvl256b_zve32x"
      "--priv=m"
      "--log-commits"
      "-m0x80000000:0x20000000,0x40000000:0x1000"
    ];
    pokedex_args = [
      "--dts-cfg-path"
      "${global-pokedex-config}"
      "--max-same-instruction"
      "20"
    ];
    end_pattern = {
      action = "write";
      memory_address = "0x40000004";
      data = "0x0";
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

    if difftest --result-path ./result.json --config-path '${configFile}'; then
      cp ./result.json "$out"
    else
      cp ./result.json "$out"

      mkdir -p "$out/pass"
      find . -name '*-pokedex-sim-event.jsonl' -type f -exec cp '{}' "$out/pass/" ';'
      find . -name '*-spike-commits.log' -type f -exec cp '{}' "$out/pass/" ';'

      failedCases=$(jq -r '.context|keys[]' ./result.json)
      for case in "''${failedCases[@]}"; do
        cp "$case".objdump "$out"
        mv "$out/pass/$(basename $case)-pokedex-sim-event.jsonl" "$out"
        mv "$out/pass/$(basename $case)-spike-commits.log" "$out"
      done
    fi
  ''
