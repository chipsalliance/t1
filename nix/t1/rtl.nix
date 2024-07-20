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
    # For ipemu, there are also some manually generated system verilog file for test bench.
    # Those files are now recorded in a individual file list.
    # However, verilator still expect on "filelist.f" file to record all the system verilog file.
    # Below is a fix that concat them into one file to make verilator happy.
    echo "Fixing generated filelist.f"
    cp $out/filelist.f original.f
    cat $out/firrtl_black_box_resource_files.f original.f > $out/filelist.f
  '';

  meta.description = "All the elaborated system verilog files for ${mlirbc.elaborateTarget} with ${mlirbc.elaborateConfig} config.";
}
