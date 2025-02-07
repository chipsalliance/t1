{ pkgs
, fetchMillDeps
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
      chiselDeps = fetchMillDeps {
        name = "chisel-snapshot";
        src = submodules.chisel.src;
        fetchTargets = [
          "unipublish"
        ];
        nativeBuildInputs = [
          git
        ];
        millDepsHash = "sha256-i5v3V/3X+EuyWy2Czhd8KYTgVQi45Ynl/dDKl3IhThI=";
      };
    in
    publishMillJar {
      name = "chisel-snapshot";
      src = submodules.chisel.src;

      publishTargets = [
        "unipublish"
      ];

      buildInputs = [
        chiselDeps.setupHook
      ];

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
        scope.ivy-chisel.chiselDeps.setupHook
        scope.ivy-chisel.setupHook
      ];

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
      arithmeticDeps = fetchMillDeps {
        name = "arithmetic-snapshot";
        src = submodules.arithmetic.src;
        buildInputs = [ scope.ivy-chisel.setupHook ];
        millDepsHash = "sha256-WNfY4zlLk+/sUoRXQsL0PBHZ5Fz8bFnpAFueJjNiSYI=";
      };
    in
    publishMillJar {
      name = "arithmetic-snapshot";
      src = submodules.arithmetic.src;

      publishTargets = [
        "arithmetic[snapshot]"
      ];

      buildInputs = [
        arithmeticDeps.setupHook
        scope.ivy-chisel.setupHook
      ];

      passthru = {
        inherit arithmeticDeps;
      };
    };

  ivy-chisel-interface =
    let
      chiselInterfaceDeps = fetchMillDeps {
        name = "chisel-interface-snapshot";
        src = submodules.chisel-interface.src;
        buildInputs = [ scope.ivy-chisel.setupHook ];
        millDepsHash = "sha256-Ktow0COOz+HDHOU2AIVaqsidHCPGjT7J+pdgpSGH0DM=";
      };
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
        chiselInterfaceDeps.setupHook
        scope.ivy-chisel.setupHook
      ];

      passthru = {
        inherit chiselInterfaceDeps;
      };
    };

  ivy-rvdecoderdb =
    let
      rvdecoderdbDeps = fetchMillDeps {
        name = "rvdecoderdb-snapshot";
        src = submodules.rvdecoderdb.src;
        millDepsHash = "sha256-j6ixFkxLsm8ihDLFkkJDePVuS2yVFmX3B1gOUVguCHQ=";
      };
    in
    publishMillJar {
      name = "rvdecoderdb-snapshot";
      src = submodules.rvdecoderdb.src;

      publishTargets = [
        "rvdecoderdb.jvm"
      ];

      buildInputs = [
        rvdecoderdbDeps.setupHook
      ];

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
      hardfloatSrc = ../../../dependencies/berkeley-hardfloat;
      hardfloatDeps = fetchMillDeps {
        name = "hardfloat-snapshot";
        src = hardfloatSrc;
        buildInputs = [ scope.ivy-chisel.setupHook ];
        millDepsHash = "sha256-lYV/BHKXpX1ssI3pZIBlzfsBclgwxVCE/TQJq/eeOcY=";
      };
    in
    publishMillJar {
      name = "hardfloat-snapshot";
      src = hardfloatSrc;

      publishTargets = [
        "hardfloat[snapshot]"
      ];

      buildInputs = [
        hardfloatDeps.setupHook
        scope.ivy-chisel.setupHook
      ];

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
