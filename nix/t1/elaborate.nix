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

    # Fix file ordering difference introduced in some unknown breaking change between firtool 1.50 -> 1.58
    # In the previous working version, all files starting with './' should be placed on top of the filelist.f.
    # However in the latest version, they were placed at the bottom, which breaks the verilator.
    # Here is an disgusting workaround to make it work. But we need to fix this issue at firtool side.
    grep '^\./' $out/filelist.f > prefixed.f
    grep -v '^\./' $out/filelist.f > not-prefixed.f
    cat prefixed.f not-prefixed.f > $out/filelist.f
  '';
}
