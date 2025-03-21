{ pkgs
, publishMillJar
, git
, makeSetupHook
, writeText
, lib
, newScope
, circt-full
, jextract-21
, runCommand
, writeShellApplication
, stdenv
, mill
, mill-ivy-fetcher
, mill-ivy-env-shell-hook
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

    passthru.bump = writeShellApplication {
      name = "bump-chisel-mill-lock";

      runtimeInputs = [
        mill
        mill-ivy-fetcher
      ];

      text = ''
        sourceDir=$(mktemp -d -t 'chisel_src_XXX')
        cp -rT ${submodules.chisel.src} "$sourceDir"/chisel
        chmod -R u+w "$sourceDir"

        mif run -p "$sourceDir"/chisel -o ./dependencies/locks/chisel-lock.nix
      '';
    };
  };

  ivy-chisel-panama =
    publishMillJar {
      name = "chisel-panama-snapshot";
      src = submodules.chisel.src;

      publishTargets = [
        "panamaconverter.cross[2.13.16]"
        "panamaom.cross[2.13.16]"
        "panamalib.cross[2.13.16]"
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

    lockFile = "${submodules.arithmetic.src}/nix/arithmetic-mill-lock.nix";
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

    lockFile = "${submodules.chisel-interface.src}/nix/chisel-interface-mill-lock.nix";

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

    lockFile = "${submodules.rvdecoderdb.src}/nix/chisel-mill-lock.nix";

    nativeBuildInputs = [
      # rvdecoderdb requires git to generate version
      git
    ];
  };

  ivy-hardfloat = publishMillJar rec {
    name = "hardfloat-snapshot";
    src = ../berkeley-hardfloat;

    publishTargets = [
      "hardfloat[snapshot]"
    ];

    buildInputs = [
      scope.ivy-chisel.setupHook
    ];

    lockFile = ../locks/berkeley-hardfloat-lock.nix;

    nativeBuildInputs = [
      # hardfloat requires git to generate version
      git
    ];

    passthru.bump = writeShellApplication {
      name = "bump-hardfloat-mill-lock";
      runtimeInputs = [
        mill
        mill-ivy-fetcher
      ];
      text = ''
        sourceDir=$(mktemp -d -t 'hardfloat_src_XXX')
        cp -rT ${src} "$sourceDir"/hardfloat
        chmod -R u+w "$sourceDir"

        ivyLocal="${scope.ivy-chisel}"
        export JAVA_TOOL_OPTIONS="''${JAVA_TOOL_OPTIONS:-} -Dcoursier.ivy.home=$ivyLocal -Divy.home=$ivyLocal"

        mif run \
          --targets 'hardfloat[snapshot]' \
          -p "$sourceDir"/hardfloat -o ./dependencies/locks/berkeley-hardfloat-lock.nix
      '';
    };
  };

  riscv-opcodes = makeSetupHook { name = "setup-riscv-opcodes-src"; } (writeText "setup-riscv-opcodes-src.sh" ''
    setupRiscvOpcodes() {
      mkdir -p dependencies
      ln -sfT "${submodules.riscv-opcodes.src}" "dependencies/riscv-opcodes"
    }
    prePatchHooks+=(setupRiscvOpcodes)
  '');

  ivyLocalRepo = runCommand "build-coursier-env"
    {
      buildInputs = with scope; [
        ivy-arithmetic.setupHook
        ivy-chisel.setupHook
        ivy-chisel-panama.setupHook
        ivy-chisel-interface.setupHook
        ivy-rvdecoderdb.setupHook
        ivy-hardfloat.setupHook
      ];
    } ''
    runHook preUnpack
    runHook postUnpack
    cp -r "$NIX_COURSIER_DIR" "$out"
  '';
})
