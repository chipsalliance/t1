{ rv32-stdenv }:
rv32-stdenv.mkDerivation {
  name = "rv32i-add.elf";

  src = ./.;

  makeFlags = [
    "RISCV_PREFIX=${rv32-stdenv.targetPlatform.config}"
    "PREFIX=${placeholder "out"}"
  ];

  passthru.dbgSrc = "/src/test.elf.objdump";

  dontFixup = true;
}
