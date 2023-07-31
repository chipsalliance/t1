final: prev:

let
  # nix cc-wrapper will add --gcc-toolchain to clang flags. However, when we want to use
  # our custom libc and compilerrt, clang will only search these libs in --gcc-toolchain 
  # folder. To avoid this weird behavior of clang, we need to remove --gcc-toolchain options
  # from cc-wrapper
  my-cc-wrapper = final.callPackage
    (
      { llvmToolsForRV32Clang, runCommand, gccForLibs }:
      let cc = llvmToolsForRV32Clang.clang; in runCommand "my-cc-wrapper" { } ''
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
  #
  # llvmForDev is used in the development shell and compiling the rv32-musl
  llvmForDev = final.llvmPackages_14;
  # llvmToolsForRV32Clang is used for compiling rv32-clang to kept compatibility with buddy-mlir
  llvmToolsForRV32Clang = (final.llvmPackages_16.override {
    gitRelease = {
      version = "17.0.0";
      rev = "8f966cedea594d9a91e585e88a80a42c04049e6c";
      rev-version = "unstable-2023-05-02";
      sha256 = "sha256-g2cYk3/iyUvmIG0QCQpYmWj4L2H4znx9KbuA5TvIjrc=";
    };
    officialRelease = null;
    buildLlvmTools = final.buildPackages.llvmToolsForRV32Clang;
  }).tools.extend (lfinal: lprev: {
    libllvm = lprev.libllvm.overrideAttrs (oldAttrs: {
      patches = (builtins.filter (p: builtins.baseNameOf p != "gnu-install-dirs.patch") oldAttrs.patches) ++ [
        ./nix/gnu-install-dirs.patch
      ];
      # Disable llvm tests here, because it wastes too much time.
      # But if someday someone bumps the new llvm, it is their responsibility to make sure
      # that these tests can pass successfully.
      doCheck = false;
    });
    libclang = lprev.libclang.overrideAttrs (oldAttrs: {
      patches = oldAttrs.patches ++ [
        ./nix/fix-clang-build.patch
      ];
    });
  });

  mill = prev.mill.override { jre = final.openjdk19; };

  espresso = final.callPackage ./nix/espresso.nix { };
  libspike = final.callPackage ./nix/libspike.nix { };
  rv32-compilerrt = final.callPackage ./nix/rv32-compilerrt.nix { };
  rv32-musl = final.callPackage ./nix/rv32-musl.nix { };
  buddy-mlir = final.callPackage ./nix/buddy-mlir.nix { };
  rvv-codegen = final.callPackage ./nix/rvv-codegen.nix { };
  rvv-testcase = final.callPackage ./nix/rvv-testcase.nix { };

  inherit rv32-clang my-cc-wrapper;
}
