{ stdenvNoCC, mill }:

stdenvNoCC.mkDerivation {
  pname = "mill-runtime";
  version = mill.version;
  dontUnpack = true;
  nativeBuildInputs = [ mill ];
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
    cp -r $TMPDIR/.mill/ammonite $out/
    runHook postInstall
  '';

  outputHashAlgo = "sha256";
  outputHashMode = "recursive";
  outputHash = "sha256-nZkMUnZHC3uDPuf69DKP9wTyICJSp28A+rYCwgIu2EA=";
}
