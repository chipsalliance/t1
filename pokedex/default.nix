{
  lib,
  newScope,
  riscv-opcodes-src,
  ivy-rvdecoderdb,
}:
lib.makeScope newScope (scope: {
  codegen-cli = scope.callPackage ./codegen/package.nix {
    inherit riscv-opcodes-src ivy-rvdecoderdb;
  };
})
