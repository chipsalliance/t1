{ lib
, stdenv
, fetchMillDeps
, makeWrapper
, jre

  # chisel deps
, mill
, espresso
, strip-nondeterminism

, submodules
}:

let
  self = stdenv.mkDerivation rec {
    name = "t1";

    src = with lib.fileset; toSource {
      root = ./../..;
      fileset = unions [
        ./../../build.sc
        ./../../common.sc
        ./../../t1
        ./../../subsystem
        ./../../rocket
        ./../../emuhelper
        ./../../ipemu/src
        ./../../elaborator
        ./../../configgen
      ];
    };

    passthru.millDeps = fetchMillDeps {
      inherit name;
      src = with lib.fileset; toSource {
        root = ./../..;
        fileset = unions [
          ./../../build.sc
          ./../../common.sc
        ];
      };
      millDepsHash = "sha256-Ri0aB4SxBdwuNOnBvvsTwVdAnvtiKIW5EMrulDX4sVo=";
      nativeBuildInputs = [ submodules.setupHook ];
    };

    passthru.editable = self.overrideAttrs (_: {
      shellHook = ''
        setupSubmodulesEditable
        mill mill.bsp.BSP/install 0
      '';
    });

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

    outputs = [ "out" "configgen" "elaborator" ];

    buildPhase = ''
      mill -i '__.assembly'
    '';

    installPhase = ''
      mkdir -p $out/share/java

      strip-nondeterminism out/elaborator/assembly.dest/out.jar
      strip-nondeterminism out/configgen/assembly.dest/out.jar

      mv out/configgen/assembly.dest/out.jar $out/share/java/configgen.jar
      mv out/elaborator/assembly.dest/out.jar $out/share/java/elaborator.jar

      mkdir -p $configgen/bin $elaborator/bin
      makeWrapper ${jre}/bin/java $configgen/bin/configgen --add-flags "-jar $out/share/java/configgen.jar"
      makeWrapper ${jre}/bin/java $elaborator/bin/elaborator --add-flags "-jar $out/share/java/elaborator.jar"
    '';
  };
in
self
