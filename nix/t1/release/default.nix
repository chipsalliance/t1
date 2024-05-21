{ lib
, newScope

, stdenvNoCC
, fetchFromGitHub
, runCommand
, makeWrapper
, typst
, jq

, configName
, t1-script
, ip
, cases
, elaborateConfigJson
, elaborateConfig
}:

let
  extension = lib.head elaborateConfig.parameter.extensions;
  isFp = lib.hasInfix "f" extension;
in

lib.makeScope newScope (scope: rec {
  inherit elaborateConfigJson configName;

  testCases = with cases; [
    intrinsic.matmul
  ] ++ lib.optionals isFp [
    intrinsic.softmax
    intrinsic.linear_normalization
  ];

  emulator-wrapped = runCommand "ip-emulator"
    {
      nativeBuildInputs = [ makeWrapper ];
    }
    ''
      mkdir -p $out/bin

      makeWrapper ${t1-script}/bin/t1-helper $out/bin/ip-emulator \
        --add-flags "ipemu" \
        --add-flags "--config ${elaborateConfigJson}" \
        --add-flags "--emulator-path ${ip.emu}/bin/emulator"

      makeWrapper ${t1-script}/bin/t1-helper $out/bin/ip-emulator-trace \
        --add-flags "ipemu" \
        --add-flags "--config ${elaborateConfigJson}" \
        --add-flags "--trace" \
        --add-flags "--emulator-path ${ip.emu-trace}/bin/emulator"
    '';

  docker-layers = scope.callPackage ./docker-layers.nix { };

  doc = stdenvNoCC.mkDerivation {
    name = "${configName}-typst-release-doc";

    nativeBuildInputs = [ typst jq ];

    src = ./doc.typ;

    unpackPhase =
      let
        typstPkgs = [
          {
            pkgName = "preview/codly";
            version = "0.2.0";
            src = fetchFromGitHub {
              owner = "Dherse";
              repo = "codly";
              rev = "0037b522957de3ab8e88cb689bf813aafa96a1b8";
              hash = "sha256-OZB9D0KpS9qVIU6O62uPQzQKx0xFxm8/nnkFU4MIkqY=";
            };
          }
        ];
      in
      ''
        export XDG_CACHE_HOME=$(mktemp -d)
        typstPkgRoot="$XDG_CACHE_HOME"/typst/packages

        ${lib.concatMapStringsSep "\n"
          (info: ''
            dstDir="$typstPkgRoot/${info.pkgName}/${info.version}"
            mkdir -p $dstDir
            cp -vrT ${info.src} "$dstDir"
          '')
          typstPkgs}
        ls -al $typstPkgRoot
      '';

    buildPhase = ''
      cp -v ${./doc.typ} ./doc.typ
      jq '.name = "${configName}"' ${elaborateConfigJson} > config.json

      mkdir $out
      typst compile \
        ./doc.typ $out/${configName}.pdf
    '';
  };
})
