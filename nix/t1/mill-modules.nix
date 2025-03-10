{ lib
, stdenv
, makeWrapper
, runCommand
, jdk21

  # chisel deps
, mill
, git
, espresso
, circt-full
, jextract-21
, add-determinism
, writeShellApplication
, glibcLocales

, submodules
, generateIvyCache
, mill-ivy-env-shell-hook
}:

let
  ivyLocalDeps = with submodules; [
    ivy-arithmetic.setupHook
    ivy-chisel.setupHook
    ivy-chisel-panama.setupHook
    ivy-chisel-interface.setupHook
    ivy-rvdecoderdb.setupHook
    ivy-hardfloat.setupHook
  ];

  t1MillDeps = generateIvyCache {
    name = "t1";
    src = self.src;
    hash = "sha256-dhBLySWC4TJJ0eMdibyAu99Efah22nO/pD2T6Qj+n98=";
    extraBuildInputs = ivyLocalDeps;
  };

  self = stdenv.mkDerivation {
    name = "t1-all-mill-modules";

    src = with lib.fileset; toSource {
      root = ./../..;
      fileset = unions [
        ./../../build.mill
        ./../../common.mill
        ./../../t1
        ./../../omreader
        ./../../omreaderlib
        ./../../t1emu/src
        ./../../elaborator
        ./../../rocketv
        ./../../t1rocket/src
        ./../../t1rocketemu/src
        ./../../stdlib/src
      ];
    };

    buildInputs = [
      submodules.riscv-opcodes
    ]
    ++ ivyLocalDeps
    ++ t1MillDeps.cache.ivyDepsList;

    nativeBuildInputs = [
      mill
      circt-full
      jextract-21
      add-determinism
      espresso
      git

      makeWrapper
    ];

    env = {
      CIRCT_INSTALL_PATH = circt-full;
      JEXTRACT_INSTALL_PATH = jextract-21;
    };

    shellHook = ''
      ${mill-ivy-env-shell-hook}

      mill -i mill.bsp.BSP/install
    '';

    outputs = [ "out" "omreader" "elaborator" ];

    buildPhase = ''
      runHook preBuild

      # Mill assume path string is always encoded in UTF-8. However in Nix
      # build environment, locale type is set to "C", and breaks the assembly
      # JAR class path. Here is the workaround for the Scala build environment.
      export LOCALE_ARCHIVE="${glibcLocales}/lib/locale/locale-archive"
      export LANG="en_US.UTF-8";

      mill -i '__.assembly'

      runHook postBuild
    '';

    installPhase = ''
      mkdir -p $out/share/java

      # Align datetime
      export SOURCE_DATE_EPOCH=1669810380
      add-determinism-q() {
        add-determinism $@ >/dev/null
      }
      add-determinism-q out/elaborator/assembly.dest/out.jar
      add-determinism-q out/omreader/assembly.dest/out.jar

      mv out/elaborator/assembly.dest/out.jar $out/share/java/elaborator.jar
      mv out/omreader/assembly.dest/out.jar "$out"/share/java/omreader.jar

      mkdir -p $elaborator/bin
      makeWrapper ${jdk21}/bin/java $elaborator/bin/elaborator \
        --add-flags "--enable-preview -Djava.library.path=${circt-full}/lib" \
        --add-flags "-cp $out/share/java/elaborator.jar"

      mkdir -p $omreader/bin
      makeWrapper ${jdk21}/bin/java "$omreader"/bin/omreader \
        --add-flags "--enable-preview" \
        --add-flags "--enable-native-access=ALL-UNNAMED" \
        --add-flags "-Djava.library.path=${circt-full}/lib" \
        --add-flags "-cp $out/share/java/omreader.jar"
    '';

    passthru = {
      inherit t1MillDeps;
    };
  };
in
self
