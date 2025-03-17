{ pkgs
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

  ivy-chisel = publishMillJar {
    name = "chisel-snapshot";
    src = submodules.chisel.src;

    lockFile = ../locks/chisel-lock.nix;

    publishTargets = [
      "unipublish"
    ];

    nativeBuildInputs = [
      # chisel requires git to generate version
      git
    ];
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
      ];

      lockFile = ../locks/chisel-lock.nix;

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

  ivy-arithmetic = publishMillJar {
    name = "arithmetic-snapshot";
    src = submodules.arithmetic.src;

    publishTargets = [
      "arithmetic[snapshot]"
    ];

    buildInputs = [
      scope.ivy-chisel.setupHook
    ];

    lockFile = ../locks/arithmetic-lock.nix;
  };

  ivy-chisel-interface = publishMillJar {
    name = "chiselInterface-snapshot";
    src = submodules.chisel-interface.src;

    publishTargets = [
      "jtag[snapshot]"
      "axi4[snapshot]"
      "dwbb[snapshot]"
    ];

    nativeBuildInputs = [ git ];

    lockFile = ../locks/chisel-interface-lock.nix;

    buildInputs = [
      scope.ivy-chisel.setupHook
    ];
  };

  ivy-rvdecoderdb = publishMillJar {
    name = "rvdecoderdb-snapshot";
    src = submodules.rvdecoderdb.src;

    publishTargets = [
      "rvdecoderdb.jvm"
    ];

    lockFile = ../locks/rvdecoderdb-lock.nix;

    nativeBuildInputs = [
      # rvdecoderdb requires git to generate version
      git
    ];
  };

  ivy-hardfloat = publishMillJar {
    name = "hardfloat-snapshot";
    src = ../berkeley-hardfloat;

    publishTargets = [
      "hardfloat[snapshot]"
    ];

    buildInputs = [
      scope.ivy-chisel.setupHook
    ];

    lockFile = ../locks/hardfloat-lock.nix;

    nativeBuildInputs = [
      # hardfloat requires git to generate version
      git
    ];
  };

  riscv-opcodes = makeSetupHook { name = "setup-riscv-opcodes-src"; } (writeText "setup-riscv-opcodes-src.sh" ''
    setupRiscvOpcodes() {
      mkdir -p dependencies
      ln -sfT "${submodules.riscv-opcodes.src}" "dependencies/riscv-opcodes"
    }
    prePatchHooks+=(setupRiscvOpcodes)
  '');
})
