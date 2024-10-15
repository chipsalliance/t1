{ lib
, stdenv
, fetchMillDeps
, makeWrapper
, jdk21

  # chisel deps
, mill
, git
, espresso
, circt-full
, jextract-21
, add-determinism

, dependencies
}:

let
  self = stdenv.mkDerivation rec {
    name = "t1-all-mill-modules";

    src = with lib.fileset; toSource {
      root = ./../..;
      fileset = unions [
        ./../../build.sc
        ./../../common.sc
        ./../../t1
        ./../../omreader
        ./../../omreaderlib
        ./../../t1emu/src
        ./../../elaborator
        ./../../rocketv
        ./../../t1rocket/src
        ./../../t1rocketemu/src
        ./../../rocketemu/src
        ./../../.scalafmt.conf
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
      millDepsHash = "sha256-XvGLNLOC7OEwfC7SB5zBdB64VjROBkwgIcHx+9FHmSs=";
      nativeBuildInputs = [ dependencies.setupHook ];
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
      git

      makeWrapper
      passthru.millDeps.setupHook

      dependencies.setupHook
    ];

    env = {
      CIRCT_INSTALL_PATH = circt-full;
      JEXTRACT_INSTALL_PATH = jextract-21;
      JAVA_TOOL_OPTIONS = "--enable-preview";
    };

    outputs = [ "out" "omreader" "elaborator" "t1package" ];

    buildPhase = ''
      runHook preBuild

      mill -i '__.assembly'

      mill -i t1package.sourceJar
      mill -i t1package.chiselPluginJar

      runHook postBuild
    '';

    installPhase = ''
      mkdir -p $out/share/java

      add-determinism-q() {
        add-determinism $@ >/dev/null
      }
      add-determinism-q out/elaborator/assembly.dest/out.jar
      add-determinism-q out/omreader/assembly.dest/out.jar
      add-determinism-q out/t1package/assembly.dest/out.jar
      add-determinism-q out/t1package/sourceJar.dest/out.jar
      add-determinism-q out/t1package/chiselPluginJar.dest/out.jar

      mv out/elaborator/assembly.dest/out.jar $out/share/java/elaborator.jar
      mv out/omreader/assembly.dest/out.jar "$out"/share/java/omreader.jar

      mkdir -p $t1package/share/java
      mv out/t1package/sourceJar.dest/out.jar $t1package/share/java/t1package-sources.jar
      mv out/t1package/assembly.dest/out.jar $t1package/share/java/t1package.jar
      mv out/t1package/chiselPluginJar.dest/out.jar $t1package/share/java/chiselPluginJar.jar

      mkdir -p $omreader/bin $elaborator/bin
      makeWrapper ${jdk21}/bin/java $elaborator/bin/elaborator \
        --add-flags "--enable-preview -Djava.library.path=${circt-full}/lib -cp $out/share/java/elaborator.jar"
      makeWrapper ${jdk21}/bin/java "$omreader"/bin/omreader \
        --add-flags "--enable-preview --enable-native-access=ALL-UNNAMED -Djava.library.path=${circt-full}/lib -jar $out/share/java/omreader.jar"
    '';
  };
in
self
