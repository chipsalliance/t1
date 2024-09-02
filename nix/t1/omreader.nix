{ lib
, stdenv
, runCommand
, fetchMillDeps
, makeWrapper
, jdk21

  # chisel deps
, mill
, espresso
, circt-full
, jextract-21
, strip-nondeterminism

, dependencies
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
      millDepsHash = "sha256-DQeKTqf+MES5ubORu+SMJEiqpOCXsS7VgxUnSqG12Bs=";
      nativeBuildInputs = [ dependencies.setupHook ];
    };

    passthru = {
      editable = self.overrideAttrs (_: {
        shellHook = ''
          setupSubmodulesEditable
          mill mill.bsp.BSP/install 0
        '';
      });

      mkWrapper = { mlirbc }: runCommand "wrap-omreader"
        { nativeBuildInputs = [ makeWrapper ]; meta.mainProgram = "omreader"; }
        ''
          mkdir -p "$out/bin"
          mlirbc=$(find ${mlirbc}/ -type f)
          makeWrapper ${self}/bin/omreader "$out/bin/omreader" --append-flags "--mlirbc-file $mlirbc"
        '';
    };

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

      dependencies.setupHook
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
