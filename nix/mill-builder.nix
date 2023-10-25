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

        local buildTasks=( $(mill -i resolve __._ | grep "prepareOffline") )
        for task in ''${buildTasks[@]}; do
          mill -i "$task" --all
        done
      '';

      installPhase = ''
        mkdir $out

        [[ -d out ]] && mv out $out/project-cache

        mkdir -p $out/mill-cache
        mv $TMPDIR/.{cache,mill} $out/mill-cache/
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

        find "$millDeps"/mill-cache -maxdepth 1 -type d -exec cp -r {} $HOME \;
        cp -r "$millDeps"/project-cache $sourceRoot/out
        chmod -R u+w -- $sourceRoot/out $HOME
        echo "JAVA HOME dir set to $HOME"
      }

      postUnpackHooks+=(setupMillCache)
    '');
}

