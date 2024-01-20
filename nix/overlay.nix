final: prev:

let
  llvmForRVV_attrName = "llvmPackages_17"; # brand new clang with v0.12 rvv intrinsic support
  rv32_pkgs = final.pkgsCross.riscv32-embedded;
  rv32_buildPkgs = rv32_pkgs.buildPackages;
in
{
  inherit rv32_pkgs rv32_buildPkgs; # for easier inspection

  espresso = final.callPackage ./pkgs/espresso.nix { };
  dramsim3 = final.callPackage ./pkgs/dramsim3.nix { };
  libspike = final.callPackage ./pkgs/libspike.nix { };
  buddy-mlir = final.callPackage ./pkgs/buddy-mlir.nix { };
  fetchMillDeps = final.callPackage ./pkgs/mill-builder.nix { };

  mill = prev.mill.override { jre = final.jdk21; };

  # some symbols in newlib libgloss uses ecall, which does not work in emulator
  # emurt provides hand-written implementations for these symbols
  emurt = final.callPackage ./pkgs/emurt {
    stdenv = rv32_pkgs.stdenv;
    bintools = rv32_buildPkgs.bintools;
  };

  # stdenv for compiling rvv programs, with ilp32f newlib and clang
  rv32-stdenv = rv32_pkgs.stdenv.override {
    cc =
      let
        major = final.lib.versions.major rv32_buildPkgs.${llvmForRVV_attrName}.release_version;

        # compiler-rt requires the compilation flag -fforce-enable-int128, only clang provides that
        compilerrt = rv32_pkgs.${llvmForRVV_attrName}.compiler-rt.override {
          stdenv = rv32_pkgs.overrideCC
            rv32_pkgs.stdenv
            rv32_buildPkgs.${llvmForRVV_attrName}.clangNoCompilerRt;
        };

        # newlib is built with double float point abi by default, override it
        newlib = rv32_pkgs.stdenv.cc.libc.overrideAttrs (oldAttrs: {
          CFLAGS_FOR_TARGET = "-march=rv32gcv -mabi=ilp32f";
        });
      in
      rv32_buildPkgs.wrapCCWith rec {
        cc = rv32_buildPkgs.${llvmForRVV_attrName}.clang-unwrapped;
        libc = newlib;
        bintools = rv32_pkgs.stdenv.cc.bintools.override {
          inherit libc; # we must keep consistency of bintools libc and compiler libc
          inherit (rv32_buildPkgs.${llvmForRVV_attrName}.bintools) bintools;
        };

        # common steps to produce clang resource directory
        extraBuildCommands = ''
          rsrc="$out/resource-root"
          mkdir "$rsrc"
          ln -s "${cc.lib}/lib/clang/${major}/include" "$rsrc"
          echo "-resource-dir=$rsrc" >> $out/nix-support/cc-cflags
          ln -s "${compilerrt}/lib" "$rsrc/lib"
          ln -s "${compilerrt}/share" "$rsrc/share"
        '';

        # link against emurt
        extraPackages = [ final.emurt ];
        nixSupport.cc-cflags = [ "-lemurt" ];
      };
  };

  t1 = final.callPackage ./t1 { };
}
