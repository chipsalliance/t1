final: prev:

let
  # nix cc-wrapper will add --gcc-toolchain to clang flags. However, when we want to use
  # our custom libc and compilerrt, clang will only search these libs in --gcc-toolchain 
  # folder. To avoid this weird behavior of clang, we need to remove --gcc-toolchain options
  # from cc-wrapper
  my-cc-wrapper = final.callPackage
    (
      { myLLVM, runCommand, gccForLibs }:
      let cc = myLLVM.clang; in runCommand "my-cc-wrapper" { } ''
        mkdir -p "$out"
        cp -rT "${cc}" "$out"
        chmod -R +w "$out"
        sed -i 's/--gcc-toolchain=[^[:space:]]*//' "$out/nix-support/cc-cflags"
        sed -i 's|${cc}|${placeholder "out"}|g' "$out"/bin/* "$out"/nix-support/*
        cat >> $out/nix-support/setup-hook <<-EOF
          export NIX_LDFLAGS_FOR_TARGET="$NIX_LDFLAGS_FOR_TARGET -L${gccForLibs.lib}/lib"
        EOF
      ''
    )
    { };

  rv32-clang = final.callPackage
    (
      { my-cc-wrapper, rv32-compilerrt, rv32-musl, writeShellScriptBin }:
      writeShellScriptBin "clang-rv32" ''
        ${my-cc-wrapper}/bin/clang --target=riscv32 -fuse-ld=lld -L${rv32-compilerrt}/lib/riscv32 -L${rv32-musl}/lib "$@"
      ''
    )
    { };

in
{
  # dont use llvmPackages = prev.llvmPackages_14 if you do not want to rebuild the world
  myLLVM = final.llvmPackages_14;
  mill = prev.mill.override { jre = final.openjdk19; };

  espresso = final.callPackage ./nix/espresso.nix { };
  libspike = final.callPackage ./nix/libspike.nix { };
  rv32-compilerrt = final.callPackage ./nix/rv32-compilerrt.nix { };
  rv32-musl = final.callPackage ./nix/rv32-musl.nix { };
  buddy-mlir = final.callPackage ./nix/buddy-mlir.nix { };

  inherit rv32-clang my-cc-wrapper;
}
