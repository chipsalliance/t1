{ lib'
, stdenv
, fetchMillDeps
, makeWrapper
, jq
, jre

  # chisel deps
, mill
, espresso
, circt
, protobuf
, antlr4

, submodules
}:

let
  self = stdenv.mkDerivation rec {
    name = "soc-elaborator";

    src = lib'.sourceFilesByPrefixes ./../.. [ "/v" "/build.sc" "/common.sc" "/subsystememu" "/rocket" ];
    sourceRoot = src.name;

    passthru.millDeps = fetchMillDeps {
      inherit name;
      src = lib'.sourceFilesByPrefixes ./../.. [ "/build.sc" "/common.sc" ];
      nativeBuildInputs = [ submodules.setupHook ];
      millDepsHash = "sha256-RIA/lgtFEaUxbZTmJaErlWcypDUdBGuK7P9iUMGtIdc=";
    };

    passthru.editable = self.overrideAttrs (_: {
      shellHook = ''
        setupSubmodulesEditable
      '';
    });

    shellHook = ''
      setupSubmodules
    '';

    nativeBuildInputs = [
      mill
      jq
      espresso
      circt
      protobuf
      antlr4
      makeWrapper
      passthru.millDeps.setupHook

      submodules.setupHook
    ];

    buildPhase = ''
      mill -i 'subsystememu.assembly'
    '';

    installPhase = ''
      mkdir -p $out/share/java
      mv out/subsystememu/assembly.dest/out.jar $out/share/java/subsystememu.jar
      makeWrapper ${jre}/bin/java $out/bin/subsystememu --add-flags "-jar $out/share/java/subsystememu.jar"
    '';

    meta.mainProgram = "subsystememu";
  };
in
self
