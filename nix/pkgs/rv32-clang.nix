{ clang, runCommand, gccForLibs, rv32-compilerrt, rv32-musl, writeShellScriptBin }:
let
  # nix cc-wrapper will add --gcc-toolchain to clang flags. However, when we want to use
  # our custom libc and compilerrt, clang will only search these libs in --gcc-toolchain 
  # folder. To avoid this weird behavior of clang, we need to remove --gcc-toolchain options
  # from cc-wrapper
  my-cc-wrapper =
    let cc = clang; in runCommand "my-cc-wrapper" { } ''
      mkdir -p "$out"
      cp -rT "${cc}" "$out"
      chmod -R +w "$out"
      sed -i 's/--gcc-toolchain=[^[:space:]]*//' "$out/nix-support/cc-cflags"
      sed -i 's|${cc}|${placeholder "out"}|g' "$out"/bin/* "$out"/nix-support/*
      cat >> $out/nix-support/setup-hook <<-EOF
        export NIX_LDFLAGS_FOR_TARGET="$NIX_LDFLAGS_FOR_TARGET -L${gccForLibs.lib}/lib"
      EOF
    '';
in
writeShellScriptBin "clang-rv32" ''
  ${my-cc-wrapper}/bin/clang --target=riscv32 -fuse-ld=lld -L${rv32-compilerrt}/lib/riscv32 -L${rv32-musl}/lib "$@"
''
