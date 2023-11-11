{ stdenvNoCC
, jq

, espresso
, circt

, elaborate-config
, elaborator
}:


stdenvNoCC.mkDerivation {
  name = "t1-elaborate";
  nativeBuildInputs = [ jq espresso circt ];
  buildCommand = ''
    jq .design < ${elaborate-config} > config.json
    mkdir -p $out
    ${elaborator}/bin/elaborator --config $(realpath config.json) --dir $out --tb true

    mfcArgs="$(jq -r '.mfcArgs[]' < "${elaborate-config}")"
    firtool $out/*.fir --annotation-file $out/*.anno.json -o $out $mfcArgs
  '';
}
