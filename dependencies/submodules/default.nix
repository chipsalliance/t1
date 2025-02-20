{ pkgs
, mill-ivy-fetcher
, publishMillJar
, git
, makeSetupHook
, writeText
, lib
, newScope
, circt-full
, jextract-21
}:


let
  submodules = lib.filterAttrs (_: v: v ? src) (pkgs.callPackage ./_sources/generated.nix { });
in
lib.makeScope newScope (scope: {
  sources = submodules;

  ivy-chisel =
    let
      chiselDeps = mill-ivy-fetcher.deps-builder ../ivys/chisel/_sources/generated.nix;
    in
    publishMillJar {
      name = "chisel-snapshot";
      src = submodules.chisel.src;

      publishTargets = [
        "unipublish"
      ];

      buildInputs = chiselDeps.ivyDepsList;

      nativeBuildInputs = [
        # chisel requires git to generate version
        git
      ];

      passthru = {
        inherit chiselDeps;
      };
    };

  ivy-chisel-panama =
    publishMillJar {
      name = "chisel-panama-snapshot";
      src = submodules.chisel.src;

      publishTargets = [
        "panamaconverter.cross[2.13.15]"
        "panamaom.cross[2.13.15]"
        "panamalib.cross[2.13.15]"
        "circtpanamabinding"
      ];

      buildInputs = [
        scope.ivy-chisel.setupHook
      ] ++ scope.ivy-chisel.chiselDeps.ivyDepsList;

      env = {
        CIRCT_INSTALL_PATH = circt-full;
        JEXTRACT_INSTALL_PATH = jextract-21;
      };

      nativeBuildInputs = [
        # chisel requires git to generate version
        git

        circt-full
        jextract-21
      ];
    };

  ivy-arithmetic =
    let
      arithmeticDeps = mill-ivy-fetcher.deps-builder ../ivys/arithmetic/_sources/generated.nix;
    in
    publishMillJar {
      name = "arithmetic-snapshot";
      src = submodules.arithmetic.src;

      publishTargets = [
        "arithmetic[snapshot]"
      ];

      buildInputs = [
        scope.ivy-chisel.setupHook
      ] ++ arithmeticDeps.ivyDepsList;

      passthru = {
        inherit arithmeticDeps;
      };
    };

  ivy-chisel-interface =
    let
      chiselInterfaceDeps = mill-ivy-fetcher.deps-builder ../ivys/chisel-interface/_sources/generated.nix;
    in
    publishMillJar {
      name = "chiselInterface-snapshot";
      src = submodules.chisel-interface.src;

      publishTargets = [
        "jtag[snapshot]"
        "axi4[snapshot]"
        "dwbb[snapshot]"
      ];

      nativeBuildInputs = [ git ];

      buildInputs = [
        scope.ivy-chisel.setupHook
      ] ++ chiselInterfaceDeps.ivyDepsList;

      passthru = {
        inherit chiselInterfaceDeps;
      };
    };

  ivy-rvdecoderdb =
    let
      rvdecoderdbDeps = mill-ivy-fetcher.deps-builder ../ivys/rvdecoderdb/_sources/generated.nix;
    in
    publishMillJar {
      name = "rvdecoderdb-snapshot";
      src = submodules.rvdecoderdb.src;

      publishTargets = [
        "rvdecoderdb.jvm"
      ];

      buildInputs = rvdecoderdbDeps.ivyDepsList;

      nativeBuildInputs = [
        # rvdecoderdb requires git to generate version
        git
      ];

      passthru = {
        inherit rvdecoderdbDeps;
      };
    };

  ivy-hardfloat =
    let
      hardfloatSrc = ../berkeley-hardfloat;
      hardfloatDeps = mill-ivy-fetcher.deps-builder ../ivys/berkeley-hardfloat/_sources/generated.nix;
    in
    publishMillJar {
      name = "hardfloat-snapshot";
      src = hardfloatSrc;

      publishTargets = [
        "hardfloat[snapshot]"
      ];

      buildInputs = [
        scope.ivy-chisel.setupHook
      ] ++ hardfloatDeps.ivyDepsList;

      nativeBuildInputs = [
        # hardfloat requires git to generate version
        git
      ];

      passthru = {
        inherit hardfloatDeps;
      };
    };

  riscv-opcodes = makeSetupHook { name = "setup-riscv-opcodes-src"; } (writeText "setup-riscv-opcodes-src.sh" ''
    setupRiscvOpcodes() {
      mkdir -p dependencies
      ln -sfT "${submodules.riscv-opcodes.src}" "dependencies/riscv-opcodes"
    }
    prePatchHooks+=(setupRiscvOpcodes)
  '');
})
