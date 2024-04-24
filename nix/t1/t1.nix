{ lib
, stdenv
, fetchMillDeps
, makeWrapper
, jdk21

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
    name = "t1";

    src = with lib.fileset; toSource {
      root = ./../..;
      fileset = unions [
        ./../../build.sc
        ./../../common.sc
        ./../../t1
        ./../../subsystem
        ./../../rocket
        ./../../emuhelper
        ./../../ipemu/src
        ./../../elaborator
        ./../../configgen
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
      circt-full
      jextract
      strip-nondeterminism

      makeWrapper
      passthru.millDeps.setupHook

      submodules.setupHook
    ];

    env.CIRCT_INSTALL_PATH = circt-full;

    outputs = [ "out" "configgen" "elaborator" "t1package" ];

    buildPhase = ''
      mill -i '__.assembly'

      mill -i t1package.sourceJar
      mill -i t1package.chiselPluginJar
    '';

    installPhase = ''
      mkdir -p $out/share/java

      strip-nondeterminism out/elaborator/assembly.dest/out.jar
      strip-nondeterminism out/configgen/assembly.dest/out.jar
      strip-nondeterminism out/t1package/assembly.dest/out.jar
      strip-nondeterminism out/t1package/sourceJar.dest/out.jar
      strip-nondeterminism out/t1package/chiselPluginJar.dest/out.jar

      mv out/configgen/assembly.dest/out.jar $out/share/java/configgen.jar
      mv out/elaborator/assembly.dest/out.jar $out/share/java/elaborator.jar

      mkdir -p $t1package/share/java
      mv out/t1package/sourceJar.dest/out.jar $t1package/share/java/t1package-sources.jar
      mv out/t1package/assembly.dest/out.jar $t1package/share/java/t1package.jar
      mv out/t1package/chiselPluginJar.dest/out.jar $t1package/share/java/chiselPluginJar.jar

      mkdir -p $configgen/bin $elaborator/bin
      makeWrapper ${jdk21}/bin/java $configgen/bin/configgen --add-flags "-jar $out/share/java/configgen.jar"
      makeWrapper ${jdk21}/bin/java $elaborator/bin/elaborator --add-flags "--enable-preview -Djava.library.path=${circt-full}/lib -jar $out/share/java/elaborator.jar"
    '';
  };
in
self
