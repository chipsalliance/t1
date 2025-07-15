{
  runCommand,
  simulator,
  all-tests,
}:
runCommand "run-pokedex-for-all-tests"
  {
    nativeBuildInputs = [
      simulator
    ];
  }
  ''
    mkdir -p "$out"

    elfs=( $(find '${all-tests}' -type f -name '*.elf') )

    for f in "''${elfs[@]}"; do
      pokedex \
        --elf-path "$f" \
        --memory-size 0xc0000000 \
        -vvv \
        -o "$out/$(basename "$f").pokedex-log.jsonl"
    done
  ''
