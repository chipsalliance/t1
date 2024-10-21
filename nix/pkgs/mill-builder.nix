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

    impureEnvVars = [ "https_proxy" "JAVA_OPTS" ];

    buildPhase = ''
      runHook preBuild

      export JAVA_OPTS="-Duser.home=$TMPDIR $JAVA_OPTS"

      if [[ -z "$https_proxy" && ! "proxyHost" =~ "$JAVA_OPTS" ]]; then
        _https_proxy="''${https_proxy#http://}"
        _https_proxy="''${_https_proxy#https://}"
        _https_proxy_parts=(''${_https_proxy//:/ })
        _host=''${_https_proxy_parts[0]}
        _port=''${_https_proxy_parts[1]}
        export JAVA_OPTS="$JAVA_OPTS -Dhttp.proxyHost=$_host -Dhttp.proxyPort=$_port -Dhttps.proxyHost=$_host -Dhttps.proxyPort=$_port"
      fi

      # Use "https://repo1.maven.org/maven2/" only to keep dependencies integrity
      export COURSIER_REPOSITORIES="central"

      mkdir -p "$TMPDIR/.mill/ammonite"
      touch "$TMPDIR/.mill/ammonite/rt-${mill-rt-version}.jar"

      mill -i __.prepareOffline
      mill -i __.scalaCompilerClasspath

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
