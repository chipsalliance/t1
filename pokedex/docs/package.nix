{
  lib,
  typst,
  model,
  stdenvNoCC,
  runCommand,
  fetchurl,
  symlinkJoin,
}:
let
  typixCommit = "b08a6f21b7ae38a0af559c4c599185d66568b846";
  fetcher = fetchurl {
    url = "https://raw.githubusercontent.com/loqusion/typix/${typixCommit}/lib/fetchTypstPackages.nix";
    hash = "sha256-gdSgkRtll6j3PhSfX3hhmHuFMwIT1FPRf1ZcDvxz9bs=";
  };
  fetchTypstPackages = (import fetcher) {
    inherit
      fetchurl
      runCommand
      lib
      symlinkJoin
      stdenvNoCC
      ;
  };
in
stdenvNoCC.mkDerivation (finalAttr: {
  name = "pokedex-doc";

  src =
    with lib.fileset;
    toSource {
      root = ./.;
      fileset = unions [
        ./doc.typ
        ./Makefile
      ];
    };

  nativeBuildInputs = [
    typst
  ];

  env = {
    TYPST_PACKAGE_CACHE_PATH = fetchTypstPackages [
      # main dependencies
      {
        name = "fletcher";
        version = "0.5.8";
        hash = "sha256-kKVp5WN/EbHEz2GCTkr8i8DRiAdqlr4R7EW6drElgWk=";
      }
      # implicit dependencies of fletcher
      {
        name = "cetz";
        version = "0.3.4";
        hash = "sha256-5w3UYRUSdi4hCvAjrp9HslzrUw7BhgDdeCiDRHGvqd4=";
      }
      {
        name = "oxifmt";
        version = "0.2.1";
        hash = "sha256-8PNPa9TGFybMZ1uuJwb5ET0WGIInmIgg8h24BmdfxlU=";
      }
    ];
  };

  buildPhase = ''
    runHook preBuild

    cp ${model}/${model.doc-comments-file} doc-comments.yml
    # Normal
    typst compile --input release=true doc.typ doc.pdf
    # Darkmode
    typst compile \
      --input release=true \
      --input enable_darkmode=true \
      doc.typ doc-dark.pdf

    runHook postBuild
  '';

  installPhase = ''
    runHook preInstall

    mkdir -p $out
    cp -v -t $out/ ./*.pdf

    runHook postInstall
  '';
})
