{ stdenvNoCC, mill, strip-nondeterminism }:

stdenvNoCC.mkDerivation {
  pname = "mill-runtime";
  version = mill.version;
  dontUnpack = true;
  nativeBuildInputs = [ mill strip-nondeterminism ];
  buildPhase = ''
    runHook preBuild
    export JAVA_OPTS="-Duser.home=$TMPDIR"
    touch build.sc
    mill -i resolve _
    runHook postBuild
  '';
  installPhase = ''
    runHook preInstall
    mkdir -p $out
    strip-nondeterminism $TMPDIR/.mill/ammonite/*
    cp -r $TMPDIR/.mill/ammonite $out/
    runHook postInstall
  '';

  outputHashAlgo = "sha256";
  outputHashMode = "recursive";
  outputHash = "sha256-tVh8IQoD1P2xmCprQgSrvhKOJGUF8M1x5afjaXsKQ8g=";
}
