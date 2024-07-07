{ lib
, stdenv
, fetchMillDeps
, jdk21

  # chisel deps
, mill
, espresso
, circt-full
, jextract-21
, add-determinism

, submodules
}:

let
  self = stdenv.mkDerivation rec {
    name = "t1-rocketv";

    src = with lib.fileset; toSource {
      root = ./../..;
      fileset = unions [
        ./../../build.sc
        ./../../common.sc
        ./../../ipemu
        ./../../subsystem
        ./../../emuhelper
        ./../../t1
        ./../../rocket
        ./../../rocketv
        ./../../elaborator
      ];
    };

    passthru.millDeps = fetchMillDeps {
      inherit name;
      src = with lib.fileset; toSource {
        root = ./../..;
        fileset = unions [
          ./../../build.sc
          ./../../common.sc
        ];
      };
      millDepsHash = "sha256-ZwIl6YsaGde3ikbzxLzY2+/XTc5O2dQrOMKcwhKEq+k=";
      nativeBuildInputs = [ submodules.setupHook ];
    };

    passthru.editable = self.overrideAttrs (_: {
      shellHook = ''
        setupSubmodulesEditable
        mill mill.bsp.BSP/install 0
      '';
    });

    shellHook = ''
      setupSubmodules
    '';

    nativeBuildInputs = [
      mill
      circt-full
      jextract-21
      add-determinism
      espresso

      passthru.millDeps.setupHook

      submodules.setupHook
    ];

    env.CIRCT_INSTALL_PATH = circt-full;

    buildPhase = ''
      mill -i elaborator.runMain org.chipsalliance.t1.elaborator.rocketv.RocketTile design \
        --parameter ./rocketv/configs/RocketTile.json --run-firtool
    '';

    installPhase = ''
      mkdir -p $out

      mv RocketTile.{sv,anno.json,fir} $out/
    '';
  };
in
self
