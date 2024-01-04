{ lib'
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

    src = lib'.sourceFilesByPrefixes ./../.. [
      "/build.sc"
      "/common.sc"
      "/t1"
      "/subsystem"
      "/rocket"
      "/emuhelper"
      "/ipemu"
      "/subsystememu"
      "/fpga"
      "/elaborator"
    ];
    sourceRoot = src.name;

    passthru.millDeps = fetchMillDeps {
      inherit name;
      src = lib'.sourceFilesByPrefixes ./../.. [ "/build.sc" "/common.sc" ];
      millDepsHash = "sha256-D93q2Z6aeXP78v61zQ3OiOOP22VDaReYx0p+bM9kcFU=";
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

    meta.mainProgram = "elaborator";
  };
in
self
