{ stdenvNoCC
, jq

, elaborator-jar
, espresso
, circt
, elaborate-config
}:


stdenvNoCC.mkDerivation {
  name = "t1-elaborate";
  nativeBuildInputs = [ jq espresso circt ];
  buildCommand = ''
    jq .design < ${elaborate-config} > config.json
    mkdir -p $out
    ${elaborator-jar}/bin/elaborator --config $(realpath config.json) --dir $out --tb true

    mfcArgs="$(jq -r '.mfcArgs[]' < "${elaborate-config}")"
    firtool $out/*.fir --annotation-file $out/*.anno.json -o $out $mfcArgs
  '';
}
