{ stdenvNoCC

, espresso
, circt

, elaborator
}:
stdenvNoCC.mkDerivation {
  name = "t1rocketemu-elaborated.mlirbc";

  nativeBuildInputs = [ elaborator espresso circt ];

  buildCommand = ''
    mkdir elaborate
    elaborator t1rocketemu --target-dir elaborate --t1rocket-config ${../configs/default.json}
    firtool elaborate/*.fir \
      --annotation-file elaborate/*.anno.json \
      --emit-bytecode \
      --parse-only \
      -o $out
  '';
}
