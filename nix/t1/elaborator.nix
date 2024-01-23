{ lib
, stdenv
, fetchMillDeps
, makeWrapper
, jdk21

  # chisel deps
, mill
, espresso
, circt-all
, jextract
, nvfetcher
, submodules
}:

let
  self = stdenv.mkDerivation rec {
    name = "t1-elaborator";

    src = (with lib.fileset; toSource {
      root = ./../..;
      fileset = unions [
        ./../../build.sc
        ./../../common.sc
        ./../../t1
        ./../../subsystem
        ./../../rocket
        ./../../emuhelper
        ./../../ipemu/src
        ./../../subsystememu
        ./../../fpga
        ./../../elaborator
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
      millDepsHash = "sha256-udjbpTLIfV/dgQxEeCJlR5IVl/avdYaPmInmqq9pwJs=";
      nativeBuildInputs = [ submodules.setupHook ];
    };

    passthru.editable = self.overrideAttrs (_: {
      nativeBuildInputs = [
        mill
        circt-all
        jextract
        makeWrapper
        passthru.millDeps.setupHook
        nvfetcher
        submodules.setupHook
        espresso
      ];
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
      circt-all
      jextract
      makeWrapper
      passthru.millDeps.setupHook

      nvfetcher
      submodules.setupHook
    ];

    env.CIRCT_INSTALL_PATH = circt-all;

    buildPhase = ''
      mill -i 'elaborator.assembly'
    '';

    installPhase = ''
      mkdir -p $out/share/java
      mv out/elaborator/assembly.dest/out.jar $out/share/java/elaborator.jar
      makeWrapper ${jdk21}/bin/java $out/bin/elaborator --add-flags "--enable-preview -Djava.library.path=${circt-all}/lib -jar $out/share/java/elaborator.jar"
    '';

    meta = {
      mainProgram = "elaborator";
      description = "The program that can be used to produce RTL";
    };
  };
in
self
