{ stdenvNoCC
, lib

, circt
, mlirbc
}:

let
  mfcArgs = lib.escapeShellArgs [
    "-O=debug"
    "--split-verilog"
    "--preserve-values=named"
    "--lowering-options=verifLabels,omitVersionComment"
    "--strip-debug-info"
  ];
in
stdenvNoCC.mkDerivation {
  name = "t1rocket-rtl";
  nativeBuildInputs = [ circt ];

  buildCommand = ''
    mkdir -p $out

    firtool ${mlirbc} ${mfcArgs} -o $out
    echo "Fixing generated filelist.f"
    cp $out/filelist.f original.f
    cat $out/firrtl_black_box_resource_files.f original.f > $out/filelist.f
  '';
}
