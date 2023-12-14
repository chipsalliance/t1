final: prev:

{
  lib' = final.callPackages ./lib.nix { };

  # llvmForDev is used in the compiling emulator, rv32-musl and rv32-compilerrt
  llvmForDev = final.llvmPackages_14;

  # llvmToolsForRV32Clang is used for compiling rv32-clang to kept compatibility with buddy-mlir
  llvmToolsForRV32Clang = final.llvmPackages_17;

  espresso = final.callPackage ./pkgs/espresso.nix { };
  libspike = final.callPackage ./pkgs/libspike.nix { };
  buddy-mlir = final.callPackage ./pkgs/buddy-mlir.nix { };
  fetchMillDeps = final.callPackage ./pkgs/mill-builder.nix { };

  mill = prev.mill.override {
    jre = final.jdk21;
  };

  rv32-compilerrt = final.callPackage ./pkgs/rv32-compilerrt.nix {
    stdenv = final.pkgsCross.riscv32-embedded.stdenv.override {
      cc = final.pkgsCross.riscv32-embedded.buildPackages.llvmPackages.clangNoCompilerRtWithLibc;
    };
    llvmPackages = final.llvmToolsForRV32Clang;
  };

  rv32-musl = final.callPackage ./pkgs/rv32-musl.nix {
    stdenv = final.pkgsCross.riscv32-embedded.stdenv.override {
      cc = final.pkgsCross.riscv32-embedded.buildPackages.llvmPackages.clangNoCompilerRt;
    };
  };
  rv32-clang = final.callPackage ./pkgs/rv32-clang.nix {
    clang = final.llvmToolsForRV32Clang.clang;
  };

  rv32-newlib-gnu-toolchain = final.callPackage ./pkgs/rv32-newlib-gnu-toolchain.nix { };
  rv32-newlib = final.pkgsCross.riscv32-embedded.newlib.overrideAttrs {
    CFLAGS_FOR_TARGET = "-march=rv32gcv -mabi=ilp32f";
    CXXFLAGS_FOR_TARGET = "-march=rv32gcv -mabi=ilp32f";
  };

  t1 = final.callPackage ./t1 { };
}
