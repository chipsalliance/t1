{ lib
, stdenv
, fetchMillDeps
, makeWrapper

, add-determinism
, metals
, mill
, mill-ivy-fetcher
}:

let
  mkHelper = { moduleName, scriptSrc, outName }:
    let
      scriptDeps = mill-ivy-fetcher.deps-builder ./ivys/_sources/generated.nix;
      self = stdenv.mkDerivation rec {
        name = "t1-${moduleName}-script";

        src = with lib.fileset; toSource {
          root = ./.;
          fileset = unions [
            scriptSrc
            ./build.sc
          ];
        };

        passthru.withLsp = self.overrideAttrs (old: {
          nativeBuildInputs = old.nativeBuildInputs ++ [
            metals
            # Metals require java to work correctly
            mill.passthru.jre
          ];

          shellHook = ''
            grep -q 'hardfloat' build.sc \
              && echo "Please run nix develop in ./script directory instead of project root" \
              && exit 1

            mill -i mill.bsp.BSP/install 0
          '';
        });

        buildInputs = scriptDeps.ivyDepsList;

        nativeBuildInputs = [
          mill
          add-determinism

          makeWrapper
        ];

        buildPhase = ''
          runHook preBuild

          echo "Building JAR"
          mill -i ${moduleName}.assembly

          runHook postBuild
        '';

        installPhase = ''
          runHook preInstall

          mkdir -p "$out"/bin

          mkdir -p $out/share/java
          mv out/${moduleName}/assembly.dest/out.jar $out/share/java/${moduleName}.jar
          # Align datetime
          export SOURCE_DATE_EPOCH=1669810380
          add-determinism $out/share/java/${moduleName}.jar
          makeWrapper ${mill.jre}/bin/java $out/bin/${outName} \
            --add-flags "-jar $out/share/java/${moduleName}.jar"

          runHook postInstall
        '';

        meta.mainProgram = toString outName;
      };
    in
    self;
in
{
  t1-helper = mkHelper { moduleName = "emu"; scriptSrc = ./emu; outName = "t1-helper"; };
  ci-helper = mkHelper { moduleName = "ci"; scriptSrc = ./ci; outName = "ci-helper"; };
}
