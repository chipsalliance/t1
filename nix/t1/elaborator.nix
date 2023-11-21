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
    name = "t1-elaborator";

    src = lib'.sourceFilesByPrefixes ./../.. [ "/v" "/build.sc" "/common.sc" "/elaborator" ];
    sourceRoot = src.name;

    passthru.millDeps = fetchMillDeps {
      inherit name;
      src = lib'.sourceFilesByPrefixes ./../.. [ "/build.sc" "/common.sc" ];
      millDepsHash = "sha256-RIA/lgtFEaUxbZTmJaErlWcypDUdBGuK7P9iUMGtIdc=";
      nativeBuildInputs = [ submodules.setupHook ];
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
