final: prev:

{
  lib' = final.callPackages ./lib.nix { };

  # llvmForDev is used in the compiling emulator, rv32-musl and rv32-compilerrt
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
        ./patches/llvm/gnu-install-dirs.patch
      ];
      # Disable llvm tests here, because it wastes too much time.
      # But if someday someone bumps the new llvm, it is their responsibility to make sure
      # that these tests can pass successfully.
      doCheck = false;
    });
    libclang = lprev.libclang.overrideAttrs (oldAttrs: {
      patches = oldAttrs.patches ++ [
        ./patches/llvm/fix-clang-build.patch
      ];
    });
  });

  mill = (prev.mill.overrideAttrs (oldAttrs: rec {
    version = "0.11.5";
    src = prev.fetchurl {
      url = "https://github.com/com-lihaoyi/mill/releases/download/${version}/${version}-assembly";
      hash = "sha256-sCJMCy4TLRQV3zI28Aydv5a8OV8OHOjLbwhfyIlxOeY=";
    };
  })).override {
    jre = final.openjdk19;
  };

  espresso = final.callPackage ./pkgs/espresso.nix { };
  libspike = final.callPackage ./pkgs/libspike.nix { };
  wal-lang = with final.python3Packages; toPythonApplication (callPackage ./pkgs/wal-lang.nix { });
  buddy-mlir = final.callPackage ./pkgs/buddy-mlir.nix { };
  mill-runtime = final.callPackage ./pkgs/mill-runtime.nix { };
  fetchMillDeps = final.callPackage ./pkgs/mill-builder.nix { };

  rv32-compilerrt = final.callPackage ./pkgs/rv32-compilerrt.nix {
    stdenv = final.llvmForDev.stdenv;
    llvmPackages = final.llvmToolsForRV32Clang;
  };
  rv32-musl = final.callPackage ./pkgs/rv32-musl.nix {
    stdenv = final.llvmForDev.stdenv.override {
      cc = final.llvmForDev.stdenv.cc.override {
        bintools = final.llvmForDev.bintools;
      };
    };
  };
  rv32-clang = final.callPackage ./pkgs/rv32-clang.nix {
    clang = final.llvmToolsForRV32Clang.clang;
  };

  rvv-codegen = final.callPackage ./testcases/rvv-codegen.nix { };
  rvv-testcases = final.callPackage ./testcases/rvv-testcases.nix {
    stdenv = final.llvmForDev.stdenv;
    llvmPackages = final.llvmForDev;
  };
  rvv-testcases-prebuilt = final.callPackage ./testcases/rvv-testcases-prebuilt.nix { };

  t1 = final.callPackage ./t1 { };
}
