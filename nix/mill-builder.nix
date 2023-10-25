# TODO: Combined them into a single lambda `buildMillPackage`?

{ stdenvNoCC, mill, writeText, makeSetupHook }:

{
  fetchMillDeps = (
    { pname
    , src
    , millDepsHash
    ,
    }: stdenvNoCC.mkDerivation {
      name = "${pname}-mill-deps";
      inherit src;

      nativeBuildInputs = [
        mill
      ];

      buildPhase = ''
        export JAVA_OPTS="-Duser.home=$TMPDIR"
        # Use "https://repo1.maven.org/maven2/" only to keep dependencies integrity
        export COURSIER_REPOSITORIES="central"

        local buildTasks=( $(mill -i resolve __._ | grep "prepareOffline") )

        for task in ''${buildTasks[@]}; do
          mill -i "$task" --all
        done
      '';

      installPhase = ''
        mkdir -p $out

        mv $TMPDIR/.cache/coursier $out/coursier
      '';

      outputHashAlgo = "sha256";
      outputHashMode = "recursive";
      outputHash = millDepsHash;

      dontShrink = true;
      dontPatchELF = true;
    }
  );

  millSetupHook = makeSetupHook
    {
      name = "mill-setup-hook.sh";
      propagatedBuildInputs = [ mill ];
    }
    (writeText "mill-setup-hook" ''
      setupMillCache() {
        export HOME=$(mktemp -d)
        export JAVA_OPTS="-Duser.home=$HOME"

        [[ -z "$millDeps" ]] && \
          echo "\$millDeps variable not found, please set mill dependencies by `millDeps` attr." && \
          exit 1

        mkdir -p $HOME/.cache
        cp -r "$millDeps"/coursier $HOME/.cache/

        chmod -R u+w -- $HOME
        echo "JAVA HOME dir set to $HOME"
      }

      postUnpackHooks+=(setupMillCache)
    '');
}

