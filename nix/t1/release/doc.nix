{ lib
, stdenvNoCC
, typst
, pandoc
}:
stdenvNoCC.mkDerivation {
  name = "t1-docker-manual";

  nativeBuildInputs = [ typst pandoc ];

  src = with lib.fileset; toSource {
    root = ./.;
    fileset = unions [
      ./doc.typ
      ./template.typ
    ];
  };

  buildPhase = ''
    runHook preBuild

    mkdir $out
    typst compile ./doc.typ $out/manual.pdf
    pandoc -f typst -t markdown ./doc.typ -o $out/manual.md

    runHook postBuild
  '';
}
