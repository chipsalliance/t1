{ lib'
, stdenv
, fetchMillDeps
, makeWrapper
, jre

  # chisel deps
, mill
, espresso

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
      millDepsHash = "sha256-3ueeJddftivvV5jQtg58sKKwXv0T2vkGxblenYFjrso=";
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
      jre

      makeWrapper
      passthru.millDeps.setupHook

      nvfetcher
      submodules.setupHook
    ];

    buildPhase = ''
      mill -i 'elaborator.assembly'
    '';

    installPhase = ''
      mkdir -p $out/share/java
      mv out/elaborator/assembly.dest/out.jar $out/share/java/elaborator.jar
      makeWrapper ${jre}/bin/java $out/bin/elaborator --add-flags "-jar $out/share/java/elaborator.jar"
    '';

    meta.mainProgram = "elaborator";
  };
in
self
