{ stdenvNoCC, mill, writeText, makeSetupHook, runCommand }:

{ name, src, millDepsHash, ... }@args:

let
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

      local buildTasks=( $(mill -i resolve __._ | grep "prepareOffline") )

      for task in ''${buildTasks[@]}; do
        echo "run mill task: $task"
        mill -i "$task" --all
      done
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

    dontFixup = true;

    passthru.setupHook = makeSetupHook
      {
        name = "mill-setup-hook.sh";
        propagatedBuildInputs = [ mill ];
      }
      (writeText "mill-setup-hook" ''
        setupMillCache() {
          export MILL_HOME=$TMPDIR/mill-cache
          export JAVA_OPTS="$JAVA_OPTS -Duser.home=$MILL_HOME"

          mkdir -p "$MILL_HOME"/.cache

          cp -r "${self}"/.cache/coursier "$MILL_HOME"/.cache/

          echo "JAVA HOME dir set to $MILL_HOME"
        }

        postUnpackHooks+=(setupMillCache)
      '');
  } // (builtins.removeAttrs args [ "name" "src" "millDepsHash" "nativeBuildInputs" ]));
in
self
