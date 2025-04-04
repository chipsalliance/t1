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
  mill-ivy-fetcher,
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
