{ lib
, stdenv
, fetchMillDeps
, makeWrapper

, add-determinism
, metals
, mill
, graalvm-ce
}:

let
  mkHelper = { moduleName, scriptSrc, outName, enableNativeExe ? true }:
    let
      self = stdenv.mkDerivation rec {
        name = "t1-${moduleName}-script";

        src = with lib.fileset; toSource {
          root = ./.;
          fileset = unions [
            scriptSrc
            ./build.sc
          ];
        };

        passthru.millDeps = fetchMillDeps {
          inherit name;
          src = with lib.fileset; toSource {
            root = ./.;
            fileset = unions [
              ./build.sc
            ];
          };
          millDepsHash = "sha256-sEOrIm7kTlpM9K6t0zma71FHFQgTHVhj5WmFwJ8UQmo=";
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

        passthru.debug = self.overrideAttrs { enableNativeExe = false; };

        nativeBuildInputs = [
          mill
          graalvm-ce
          add-determinism

          makeWrapper
          passthru.millDeps.setupHook
        ];

        inherit enableNativeExe;

        buildPhase = ''
          runHook preBuild

          echo "Building JAR"
          mill -i ${moduleName}.assembly

          if (( $enableNativeExe )); then
            echo "Running native-image"
            native-image --no-fallback -jar out/${moduleName}/assembly.dest/out.jar "$name.elf"
          fi

          runHook postBuild
        '';

        installPhase = ''
          runHook preInstall

          mkdir -p "$out"/bin

          if (( $enableNativeExe )); then
            cp "$name.elf" "$out"/bin/"${outName}"
          else
            mkdir -p $out/share/java
            mv out/${moduleName}/assembly.dest/out.jar $out/share/java/${moduleName}.jar
            add-determinism $out/share/java/${moduleName}.jar
            makeWrapper ${mill.jre}/bin/java $out/bin/${outName} \
              --add-flags "-jar $out/share/java/${moduleName}.jar"
          fi

          runHook postInstall
        '';

        meta.mainProgram = toString outName;
      };
    in
    self;
in
{
  t1-helper = mkHelper { moduleName = "emu"; scriptSrc = ./emu; outName = "t1-helper"; };
  ci-helper = mkHelper { moduleName = "ci"; scriptSrc = ./ci; outName = "ci-helper"; enableNativeExe = false; };
}
