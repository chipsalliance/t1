{ lib
, stdenv
, fetchMillDeps
, makeWrapper
, jdk21

, mill
, espresso
, circt-full
, jextract-21

, submodules
}:

let
  self = stdenv.mkDerivation rec {
    name = "chisel-interface";

    src = with lib.fileset; toSource {
      root = ./..;
      fileset = unions [
        ./../build.sc
        ./../common.sc
        ./../axi4
        ./../dwbb
      ];
    };

    passthru.millDeps = fetchMillDeps {
      inherit name;
      src = with lib.fileset; toSource {
        root = ./..;
        fileset = unions [
          ./../build.sc
          ./../common.sc
        ];
      };
      millDepsHash = "sha256-mN/nghQP9VN5h2UpY0k6MHcKdaFoZ5sK1aG84giqVY8=";
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

      makeWrapper
      passthru.millDeps.setupHook

      submodules.setupHook
    ];

    env.CIRCT_INSTALL_PATH = circt-full;

    outputs = [ "out" ];
  };
in
self
