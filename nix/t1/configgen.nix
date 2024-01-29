{ lib
, stdenvNoCC
, fetchMillDeps
, mill
, makeWrapper
, jre
, strip-nondeterminism
, submodules
}:

stdenvNoCC.mkDerivation rec {
  name = "t1-configgen";

  src = (with lib.fileset; toSource {
    root = ./../..;
    fileset = unions [
      ./../../build.sc
      ./../../common.sc
      ./../../t1
      ./../../configgen
    ];
  }).outPath;

  passthru.millDeps = fetchMillDeps {
    inherit name;
    src = (with lib.fileset; toSource {
      root = ./../..;
      fileset = unions [
        ./../../build.sc
        ./../../common.sc
      ];
    }).outPath;
    millDepsHash = "sha256-tZJEIN1JtZlrRSPKyVBgeq0cgAYWEJ6NssN5l6kS7ME=";
    nativeBuildInputs = [ submodules.setupHook ];
  };

  shellHook = ''
    setupSubmodules
  '';

  nativeBuildInputs = [
    mill
    strip-nondeterminism

    makeWrapper
    passthru.millDeps.setupHook
    submodules.setupHook
  ];

  buildPhase = ''
    mill -i 'configgen.assembly'
  '';

  installPhase = ''
    mkdir -p $out/share/java
    strip-nondeterminism out/configgen/assembly.dest/out.jar
    mv out/configgen/assembly.dest/out.jar $out/share/java/configgen.jar
    makeWrapper ${jre}/bin/java $out/bin/configgen --add-flags "-jar $out/share/java/configgen.jar"

    $out/bin/configgen listConfigs -o $out/share/all-supported-configs.json
  '';

  meta = {
    mainProgram = "configgen";
    description = "Generator for T1 RTL configuration";
  };
}
