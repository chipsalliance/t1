{ lib
, stdenv
, fetchMillDeps
, makeWrapper

, metals
, mill
, graalvm-ce
}:

let
  self = stdenv.mkDerivation rec {
    name = "t1-helper";

    src = with lib.fileset; toSource {
      root = ./.;
      fileset = unions [
        ./src
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
      millDepsHash = "sha256-J8bBgM/F+8x8EQ1DR6Va/ZY2hnsjkkzk4a+ctDMKK3k=";
    };

    passthru.dev = self.overrideAttrs (old: {
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

      rs-toolchain
      pkgs.rust-analyzer-unwrapped
    ];

    buildPhase = ''
      echo "Building JAR"
      mill -i assembly
      echo "Running native-image"
      native-image --no-fallback -jar out/assembly.dest/out.jar "$name.elf"
    '';

    installPhase = ''
      mkdir -p "$out"/bin
      cp "$name.elf" "$out"/bin/"$name"
    '';
  };
in
self
