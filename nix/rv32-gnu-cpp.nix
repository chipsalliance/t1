{ writeShellScriptBin, rv32-gnu-toolchain }:
writeShellScriptBin "rv32-g++" ''
  ${rv32-gnu-toolchain}/bin/riscv32-unknown-elf-g++ -mabi=ilp32f -march=rv32gcv -static "$@"
''
