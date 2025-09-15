{
  lib,
  stdenv,
  makeWrapper,

  add-determinism,
  metals,
  mill,
  ivy-gather,
  mill-ivy-env-shell-hook,
}:

let
  mkHelper =
    {
      moduleName,
      scriptSrc,
      outName,
    }:
    let
      scriptDeps = ivy-gather ./script-lock.nix;
      self = stdenv.mkDerivation {
        name = "t1-${moduleName}-script";

        src =
          with lib.fileset;
          toSource {
            root = ./.;
            fileset = unions [
              scriptSrc
              ./build.mill
            ];
          };

        passthru.withLsp = self.overrideAttrs (old: {
          nativeBuildInputs = old.nativeBuildInputs ++ [
            metals
            # Metals require java to work correctly
            mill.passthru.jre
          ];

          shellHook = ''
            ${mill-ivy-env-shell-hook}

            grep -q 'hardfloat' build.sc \
              && echo "Please run nix develop in ./script directory instead of project root" \
              && exit 1

            mill -i mill.bsp.BSP/install 0
          '';
        });

        buildInputs = [ scriptDeps ];

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
          add-det $out/share/java/${moduleName}.jar
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
  t1-helper = mkHelper {
    moduleName = "emu";
    scriptSrc = ./emu;
    outName = "t1-helper";
  };
  ci-helper = mkHelper {
    moduleName = "ci";
    scriptSrc = ./ci;
    outName = "ci-helper";
  };
}
