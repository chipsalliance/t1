{ lib
, stdenvNoCC
, mill
, makeWrapper
, jre
, submodules

, _t1CompileCache
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

  passthru = {
    inherit (_t1CompileCache) millDeps;
  };

  shellHook = ''
    setupSubmodules
  '';

  nativeBuildInputs = [
    mill

    makeWrapper
    passthru.millDeps.setupHook
    _t1CompileCache.setupHook
    submodules.setupHook
  ];

  buildPhase = ''
    mill -i --jobs 0 'configgen.assembly'
  '';

  installPhase = ''
    mkdir -p $out/share/java
    mv out/configgen/assembly.dest/out.jar $out/share/java/configgen.jar
    makeWrapper ${jre}/bin/java $out/bin/configgen --add-flags "-jar $out/share/java/configgen.jar"

    $out/bin/configgen listConfigs -o $out/share/all-supported-configs.json
  '';

  meta = {
    mainProgram = "configgen";
    description = "Generator for T1 RTL configuration";
  };
}
