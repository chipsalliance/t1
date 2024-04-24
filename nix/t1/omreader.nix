{ lib
, stdenv
, fetchMillDeps
, makeWrapper

  # chisel deps
, mill
, espresso
, circt-full
, jextract
, strip-nondeterminism

, submodules
}:

let
  self = stdenv.mkDerivation rec {
    name = "omreader";

    src = with lib.fileset; toSource {
      root = ./../..;
      fileset = unions [
        ./../../build.sc
        ./../../common.sc
        ./../../omreader
        ./../../omreaderlib
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
      millDepsHash = "sha256-rkS/bTDnjnyzdQyTIhfLj3e0mMdDn4fzv/660rO3qYg=";
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
      jextract
      strip-nondeterminism
      circt-full

      makeWrapper
      passthru.millDeps.setupHook

      submodules.setupHook
    ];

    env = {
      CIRCT_INSTALL_PATH = circt-full;
      JAVA_TOOL_OPTIONS = "--enable-preview";
    };

    buildPhase = ''
      mill -i 'omreader.assembly'
    '';

    installPhase = ''
      mkdir -p "$out"/bin "$out"/share/java
      strip-nondeterminism out/omreader/assembly.dest/out.jar
      mv out/omreader/assembly.dest/out.jar "$out"/share/java/omreader.jar
      makeWrapper ${mill.jre}/bin/java "$out"/bin/omreader --add-flags "--enable-preview -Djava.library.path=${circt-full}/lib -jar $out/share/java/omreader.jar"
    '';

    meta = {
      description = "omreader";
      mainProgram = "omreader";
    };
  };
in
self
