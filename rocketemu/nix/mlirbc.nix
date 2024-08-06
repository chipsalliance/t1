{ stdenvNoCC

, espresso
, circt

, elaborator
, rocket-config
}:
stdenvNoCC.mkDerivation {
  name = "t1-rocketv-elaborated.mlirbc";

  nativeBuildInputs = [ elaborator espresso circt ];

  buildCommand = ''
    mkdir elaborate
    elaborator rocketemu --target-dir elaborate --rocket-config ${rocket-config}
    firtool elaborate/*.fir \
      --annotation-file elaborate/*.anno.json \
      --emit-bytecode \
      --parse-only \
      -o $out
  '';
}
