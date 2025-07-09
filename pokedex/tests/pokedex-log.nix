{
  runCommand,
  simulator,
  test-elf,
}:
runCommand "run-pokedex-for-${test-elf.name}"
  {
    nativeBuildInputs = [
      simulator
    ];
  }
  ''
    mkdir -p "$out"

    pokedex \
      --elf-path "${test-elf}/bin/test.elf" \
      --memory-size 0xc0000000 \
      -vvv \
      -o "$out/pokedex.log.jsonl"
  ''
