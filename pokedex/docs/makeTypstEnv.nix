{
  lib,
  stdenvNoCC,
  typst,
  fetchurl,
  callPackage,
}:

let
  typixCommit = "b08a6f21b7ae38a0af559c4c599185d66568b846";
  typixFetcher = fetchurl {
    url = "https://raw.githubusercontent.com/loqusion/typix/${typixCommit}/lib/fetchTypstPackages.nix";
    hash = "sha256-gdSgkRtll6j3PhSfX3hhmHuFMwIT1FPRf1ZcDvxz9bs=";
  };
  fetchTypstPackages = callPackage typixFetcher { };
in

{
  name,
  sources,
  plugins ? [ ],
  typstPhase,
  ...
}@inputs:
stdenvNoCC.mkDerivation (
  lib.recursiveUpdate
    {
      inherit name typstPhase;

      src =
        with lib.fileset;
        toSource {
          inherit (sources) root;
          fileset = unions sources.files;
        };

      nativeBuildInputs = [
        typst
      ];

      env = {
        TYPST_PACKAGE_CACHE_PATH = fetchTypstPackages plugins;
      };

      buildPhase = ''
        runHook preBuild

        runPhase typstPhase

        runHook postBuild
      '';

      installPhase = ''
        runHook preInstall

        mkdir -p $out
        cp -v -t $out/ ./*.pdf

        runHook postInstall
      '';
    }
    (
      lib.removeAttrs inputs [
        "name"
        "sources"
        "plugins"
        "typstPhase"
      ]
    )
)
