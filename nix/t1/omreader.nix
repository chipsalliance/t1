{ lib
, stdenv
, fetchMillDeps
, makeWrapper
, jdk21

  # chisel deps
, mill
, espresso
, circt-full
, jextract-21
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
      millDepsHash = "sha256-ZgcBH7p4/F8Jn6qmsTKmwN6PLVXi37iuRCD0xYxvEO4=";
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
      jextract-21
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
      makeWrapper ${jdk21}/bin/java "$out"/bin/omreader --add-flags "--enable-preview --enable-native-access=ALL-UNNAMED -Djava.library.path=${circt-full}/lib -jar $out/share/java/omreader.jar"
      echo "$(cat "$out"/bin/omreader) 2> /dev/null" > "$out"/bin/omreader
    '';

    meta = {
      description = "CLI reads OM properties from MLIR bytecodes";
      mainProgram = "omreader";
    };
  };
in
self
