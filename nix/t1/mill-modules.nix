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
, writeShellApplication
, glibcLocales

, submodules
, mill-ivy-fetcher
}:

let
  t1MillDeps = mill-ivy-fetcher.deps-builder ../../dependencies/ivys/t1/_sources/generated.nix;
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

    buildInputs = with submodules; [
      ivy-arithmetic.setupHook
      ivy-chisel.setupHook
      ivy-chisel-panama.setupHook
      ivy-chisel-interface.setupHook
      ivy-rvdecoderdb.setupHook
      ivy-hardfloat.setupHook
      riscv-opcodes
    ] ++ t1MillDeps.ivyDepsList;

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
      export NIX_COURSIER_HOME="$(mktemp -d -t 'coursier_repo_XXX')"
      mkdir -p "$NIX_COURSIER_HOME/local"
      export COURSIER_REPOSITORIES="ivy2Local|central"
      export JAVA_TOOL_OPTIONS="-Dcoursier.ivy.home=$NIX_COURSIER_HOME $JAVA_TOOL_OPTIONS"

      lndir ${submodules.ivy-arithmetic}/.ivy2/local "$NIX_COURSIER_HOME/local"
      lndir ${submodules.ivy-chisel}/.ivy2/local "$NIX_COURSIER_HOME/local"
      lndir ${submodules.ivy-chisel-panama}/.ivy2/local "$NIX_COURSIER_HOME/local"
      lndir ${submodules.ivy-chisel-interface}/.ivy2/local "$NIX_COURSIER_HOME/local"
      lndir ${submodules.ivy-rvdecoderdb}/.ivy2/local "$NIX_COURSIER_HOME/local"
      lndir ${submodules.ivy-hardfloat}/.ivy2/local "$NIX_COURSIER_HOME/local"
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

    passthru.format = writeShellApplication {
      name = "mill-format-for-t1";
      runtimeInputs = [ mill ];
      text = ''
        subcmd="''${1:-}"
        [[ -z "$subcmd" ]] && \
          echo "no subcmd specify, available: (check, run)" >&2 && exit 1

        _targetsToCheck=(
          "elaborator"
          "omreader"
          "omreaderlib"
          "rocketv"
          "t1"
          "t1emu"
          "t1rocket"
          "t1rocketemu"
        )

        case "$subcmd" in
          check)
            for _t in "''${_targetsToCheck[@]}"; do
              if ! mill -i "$_t".checkFormat; then
                echo "[ERROR] Please run 'mill -i $_t.reformat' before elaborate!" >&2
                exit 1
              fi
            done
            ;;
          run)
            for _t in "''${_targetsToCheck[@]}"; do
              mill -i "$_t".reformat || true
            done
            ;;
          *)
            echo "Invalid subcmd $subcmd, available: (check, run)" >&2
            exit 1
            ;;
        esac
      '';
    };
  };
in
self
