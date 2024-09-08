{ lib
, stdenv
, fetchMillDeps
, makeWrapper

, metals
, mill
, graalvm-ce
}:

let
  mkHelper = { moduleName, scriptSrc, outName }:
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
          millDepsHash = "sha256-QQ5gCbvovC55t9MmfCNTvNFdD6FcNqmLmfhT9qJhQQc=";
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

        nativeBuildInputs = [
          mill
          graalvm-ce

          makeWrapper
          passthru.millDeps.setupHook
        ];

        buildPhase = ''
          echo "Building JAR"
          mill -i ${moduleName}.assembly
          echo "Running native-image"
          native-image --no-fallback -jar out/${moduleName}/assembly.dest/out.jar "$name.elf"
        '';

        installPhase = ''
          mkdir -p "$out"/bin
          cp "$name.elf" "$out"/bin/"${outName}"
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
