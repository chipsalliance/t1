{ self }:

final: prev:

let
  llvmForRVV_attrName = "llvmPackages_17"; # brand new clang with v0.12 rvv intrinsic support
  rv32_pkgs = final.pkgsCross.riscv32-embedded;
  rv32_buildPkgs = rv32_pkgs.buildPackages;
in
{
  pkgsX86 = self.legacyPackages."x86_64-linux";

  inherit rv32_pkgs rv32_buildPkgs; # for easier inspection

  # Override "nixpkgs" circt with "nixpkgs-for-circt".
  # To update the "nixpkgs-for-circt" input, run `nix flake lock --update-input nixpkgs-for-circt`.
  circt = self.inputs.nixpkgs-for-circt.legacyPackages."${final.system}".circt;
  espresso = final.callPackage ./pkgs/espresso.nix { };
  dramsim3 = final.callPackage ./pkgs/dramsim3.nix { };
  libspike = final.callPackage ./pkgs/libspike.nix { };
  libspike_interfaces = final.callPackage ../difftest/libspike_interfaces { };
  buddy-mlir = final.callPackage ./pkgs/buddy-mlir.nix { };
  fetchMillDeps = final.callPackage ./pkgs/mill-builder.nix { };
  circt-full = final.callPackage ./pkgs/circt-full.nix { };
  rvv-codegen = final.callPackage ./pkgs/rvv-codegen.nix { };
  add-determinism = final.callPackage ./pkgs/add-determinism { };  # faster strip-undetereminism

  mill = let jre = final.jdk21; in
    (prev.mill.override { inherit jre; }).overrideAttrs (_: {
      passthru = { inherit jre; };
    });

  # some symbols in newlib libgloss uses ecall, which does not work in emulator
  # emurt provides hand-written implementations for these symbols
  emurt = final.callPackage ../tests/emurt {
    stdenv = rv32_pkgs.stdenv;
    bintools = rv32_buildPkgs.bintools;
  };

  t1-script = final.callPackage ../script { };

  # stdenv for compiling rvv programs, with ilp32f newlib and clang
  rv32-stdenv = rv32_pkgs.stdenv.override {
    cc =
      let
        major = final.lib.versions.major rv32_buildPkgs.${llvmForRVV_attrName}.release_version;

        # By default, compiler-rt and newlib for rv32 are built with double float point abi by default.
        # We need to override it with `-mabi=ilp32f`

        # compiler-rt requires the compilation flag -fforce-enable-int128, only clang provides that
        compilerrt = (rv32_pkgs.${llvmForRVV_attrName}.compiler-rt.override {
          stdenv = rv32_pkgs.overrideCC
            rv32_pkgs.stdenv
            rv32_buildPkgs.${llvmForRVV_attrName}.clangNoCompilerRt;
        }).overrideAttrs (oldAttrs: {
          env.NIX_CFLAGS_COMPILE = "-march=rv32gcv -mabi=ilp32f";
        });

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
