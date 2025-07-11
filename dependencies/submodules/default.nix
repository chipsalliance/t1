{
  pkgs,
  publishMillJar,
  git,
  makeSetupHook,
  writeText,
  lib,
  newScope,
  circt-install,
  mlir-install,
  jextract-21,
  runCommand,
  writeShellApplication,
  mill,
  mill_0_12_14,
  mill-ivy-fetcher,
}:

let
  submodules = lib.filterAttrs (_: v: v ? src) (pkgs.callPackage ./_sources/generated.nix { });
in
lib.makeScope newScope (scope: {
  sources = submodules;

  ivy-chisel =
    (publishMillJar.override {
      mill = mill_0_12_14;
    })
      {
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
            mill_0_12_14
            mill-ivy-fetcher
          ];

          text = ''
            mif run -p "${submodules.chisel.src}" -o ./dependencies/locks/chisel-lock.nix "$@"
          '';
        };
      };

  ivy-omlib = publishMillJar {
    name = "omlib-snapshot";
    src = submodules.zaozi.src;

    publishTargets = [
      "mlirlib"
      "circtlib"
      "omlib"
    ];

    env = {
      CIRCT_INSTALL_PATH = circt-install;
      MLIR_INSTALL_PATH = mlir-install;
      JEXTRACT_INSTALL_PATH = jextract-21;
      JAVA_TOOL_OPTIONS = "-Djextract.decls.per.header=65535 --enable-preview";
    };

    lockFile = ../locks/zaozi-lock.nix;

    passthru.bump = writeShellApplication {
      name = "bump-zaozi-mill-lock";

      runtimeInputs = [
        mill
        mill-ivy-fetcher
      ];

      text = ''
        mif run -p "${submodules.zaozi.src}" -o ./dependencies/locks/zaozi-lock.nix "$@"
      '';
    };

    nativeBuildInputs = [ git ];
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

    lockFile = ../locks/arithmetic-mill-lock.nix;

    passthru.bump = writeShellApplication {
      name = "bump-arithmetic-mill-lock";

      runtimeInputs = [
        mill
        mill-ivy-fetcher
      ];

      text = ''
        ivyLocal="${scope.ivy-chisel}"
        export JAVA_TOOL_OPTIONS="''${JAVA_TOOL_OPTIONS:-} -Dcoursier.ivy.home=$ivyLocal -Divy.home=$ivyLocal"

        mif run -p "${submodules.arithmetic.src}" \
          --targets "arithmetic[snapshot]" \
          -o ./dependencies/locks/arithmetic-mill-lock.nix "$@"
      '';
    };
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

    passthru.bump = writeShellApplication {
      name = "bump-chisel-interface-mill-lock";

      runtimeInputs = [
        mill
        mill-ivy-fetcher
      ];

      text = ''
        ivyLocal="${scope.ivy-chisel}"
        export JAVA_TOOL_OPTIONS="''${JAVA_TOOL_OPTIONS:-} -Dcoursier.ivy.home=$ivyLocal -Divy.home=$ivyLocal"

        mif run -p "${submodules.chisel-interface.src}" \
          --targets "jtag[snapshot]" \
          --targets "axi4[snapshot]" \
          --targets "dwbb[snapshot]" \
          -o ./dependencies/locks/chisel-interface-lock.nix "$@"
      '';
    };
  };

  ivy-rvdecoderdb = publishMillJar rec {
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

    passthru.bump = writeShellApplication {
      name = "bump-rvdecoderdb-mill-lock";
      runtimeInputs = [
        mill
        mill-ivy-fetcher
      ];
      text = ''
        mif run \
          --targets 'rvdecoderdb.jvm' \
          -p "${src}" -o ./dependencies/locks/rvdecoderdb-lock.nix "$@"
      '';
    };
  };

  ivy-rvdecoderdb3 = publishMillJar rec {
    name = "rvdecoderdb-3-snapshot";
    src = submodules.zaozi.src;

    publishTargets = [
      "rvdecoderdb"
    ];

    lockFile = ../locks/rvdecoderdb-3-lock.nix;

    nativeBuildInputs = [
      # rvdecoderdb requires git to generate version
      git
    ];

    passthru.bump = writeShellApplication {
      name = "bump-rvdecoderdb-3-mill-lock";
      runtimeInputs = [
        mill
        mill-ivy-fetcher
      ];
      text = ''
        mif run \
          --targets 'rvdecoderdb' \
          -p "${src}" -o ./dependencies/locks/rvdecoderdb-3-lock.nix "$@"
      '';
    };
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
        ivyLocal="${scope.ivy-chisel}"
        export JAVA_TOOL_OPTIONS="''${JAVA_TOOL_OPTIONS:-} -Dcoursier.ivy.home=$ivyLocal -Divy.home=$ivyLocal"

        mif run \
          --targets 'hardfloat[snapshot]' \
          -p "${src}" -o ./dependencies/locks/berkeley-hardfloat-lock.nix "$@"
      '';
    };
  };

  riscv-opcodes = makeSetupHook { name = "setup-riscv-opcodes-src"; } (
    writeText "setup-riscv-opcodes-src.sh" ''
      setupRiscvOpcodes() {
        mkdir -p dependencies
        ln -sfT "${submodules.riscv-opcodes.src}" "dependencies/riscv-opcodes"
      }
      prePatchHooks+=(setupRiscvOpcodes)
    ''
  );

  ivyLocalRepo =
    runCommand "build-coursier-env"
      {
        buildInputs = with scope; [
          ivy-arithmetic.setupHook
          ivy-chisel.setupHook
          ivy-omlib.setupHook
          ivy-chisel-interface.setupHook
          ivy-rvdecoderdb.setupHook
          ivy-hardfloat.setupHook
        ];
      }
      ''
        runHook preUnpack
        runHook postUnpack
        cp -r "$NIX_COURSIER_DIR" "$out"
      '';
})
