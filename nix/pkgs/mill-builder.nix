{ stdenvNoCC, mill, writeText, makeSetupHook, runCommand, lib }:

{ name, src, millDepsHash, ... }@args:

let
  mill-rt-version = lib.head (lib.splitString "+" mill.jre.version);
  self = stdenvNoCC.mkDerivation ({
    name = "${name}-mill-deps";
    inherit src;

    nativeBuildInputs = [
      mill
    ] ++ (args.nativeBuildInputs or [ ]);

    impureEnvVars = [ "JAVA_OPTS" ];

    buildPhase = ''
      runHook preBuild
      export JAVA_OPTS="-Duser.home=$TMPDIR $JAVA_OPTS"

      # Use "https://repo1.maven.org/maven2/" only to keep dependencies integrity
      export COURSIER_REPOSITORIES="central"

      mkdir -p "$TMPDIR/.mill/ammonite"
      touch "$TMPDIR/.mill/ammonite/rt-${mill-rt-version}.jar"

      mill -i __.prepareOffline
      mill -i __.scalaCompilerClasspath

      # mill doesn't put scalafmt version into module ivy deps.
      # It downloads scalafmt only when checkFormat/reformat is explicitly trigger.
      # And we don't want to wait too long for a dependencies task, so here is the solution:
      # "checkFormat" the "build.sc" file so that mill will download scalafmt for us,
      # and we don't need to wait too long for formatting.
      mill -i mill.scalalib.scalafmt.ScalafmtModule/checkFormatAll buildSources

      runHook postBuild
    '';

    installPhase = ''
      runHook preInstall
      mkdir -p $out/.cache
      mv $TMPDIR/.cache/coursier $out/.cache/coursier
      runHook postInstall
    '';

    outputHashAlgo = "sha256";
    outputHashMode = "recursive";
    outputHash = millDepsHash;

    dontShrink = true;
    dontPatchELF = true;

    passthru.setupHook = makeSetupHook
      {
        name = "mill-setup-hook.sh";
        propagatedBuildInputs = [ mill ];
      }
      (writeText "mill-setup-hook" ''
        setupMillCache() {
          local tmpdir=$(mktemp -d)
          export JAVA_OPTS="$JAVA_OPTS -Duser.home=$tmpdir"

          mkdir -p "$tmpdir"/.cache "$tmpdir/.mill/ammonite"

          cp -r "${self}"/.cache/coursier "$tmpdir"/.cache/
          touch "$tmpdir/.mill/ammonite/rt-${mill-rt-version}.jar"

          echo "JAVA HOME dir set to $tmpdir"
        }

        postUnpackHooks+=(setupMillCache)
      '');
  } // (builtins.removeAttrs args [ "name" "src" "millDepsHash" "nativeBuildInputs" ]));
in
self
