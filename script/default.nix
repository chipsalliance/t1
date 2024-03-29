{ lib
, stdenv
, fetchMillDeps
, makeWrapper

, metals
, gnugrep
, jre
, mill
, strip-nondeterminism
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
      millDepsHash = "sha256-/0wAHQnMG9Zxwkpb1ZDobYDzJe4tN+/rJvHZFytV4lg=";
    };

    passthru.dev = self.overrideAttrs (old: {
      nativeBuildInputs = old.nativeBuildInputs ++ [
        metals
        gnugrep
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
      strip-nondeterminism

      makeWrapper
      passthru.millDeps.setupHook
    ];

    buildPhase = ''
      mill -i assembly
    '';

    installPhase = ''
      mkdir -p $out/share/java "$out"/bin

      strip-nondeterminism out/assembly.dest/out.jar

      mv out/assembly.dest/out.jar $out/share/java/"$name".jar

      makeWrapper ${jre}/bin/java "$out"/bin/"$name" --add-flags "-jar $out/share/java/$name.jar"
    '';
  };
in
self
