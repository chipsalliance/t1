{ stdenvNoCC

, espresso
, circt

, rocketv
}:
stdenvNoCC.mkDerivation {
  name = "t1-rocketv-elaborated.mlirbc";

  nativeBuildInputs = [ espresso circt ];

  buildCommand = ''
    firtool ${rocketv}/*.fir \
      --annotation-file ${rocketv}/*.anno.json \
      --emit-bytecode \
      --parse-only \
      -o $out
  '';
}
