{ stdenvNoCC
, lib
, mfcArgs
, circt
, mlirbc
}:

let
  fixupFilelist = lib.elem mlirbc.elaborateTarget [ "ipemu" ];
in
stdenvNoCC.mkDerivation {
  name = "t1-${mlirbc.elaborateConfig}-${mlirbc.elaborateTarget}-rtl";
  nativeBuildInputs = [ circt ];

  passthru = {
    inherit (mlirbc) elaborateTarget elaborateConfig;
  };

  buildCommand = ''
    mkdir -p $out

    firtool ${mlirbc}/${mlirbc.elaborateTarget}-${mlirbc.elaborateConfig}-lowered.mlirbc \
      -o $out ${mfcArgs}
  '' + lib.optionalString fixupFilelist ''
    # FIXME: https://github.com/llvm/circt/pull/7543
    echo "Fixing generated filelist.f"
    pushd $out
    find . -mindepth 1 -name '*.sv' -type f > $out/filelist.f
    popd
  '';

  meta.description = "All the elaborated system verilog files for ${mlirbc.elaborateTarget} with ${mlirbc.elaborateConfig} config.";
}
