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
    "--output-annotation-file=mfc.anno.json"
    "--lowering-options=verifLabels"
  ];
  fixupFilelist = lib.elem mlirbc.elaborateTarget [ "ipemu" "subsystememu" ];
in
stdenvNoCC.mkDerivation {
  name = "t1-${mlirbc.elaborateConfig}-${mlirbc.elaborateTarget}-rtl";
  nativeBuildInputs = [ circt ];

  passthru = {
    inherit (mlirbc) elaborateTarget elaborateConfig;
  };

  buildCommand = ''
    mkdir -p $out

    firtool ${mlirbc}/*.mlirbc -o $out ${mfcArgs}
  '' + lib.optionalString fixupFilelist ''
    # For ipemu and subsystememu, there are also some manually generated system verilog file for test bench.
    # Those files are not correctly handled by firtool.
    #
    # Fix file ordering difference introduced in some unknown breaking change between firtool 1.50 -> 1.58
    # In the previous working version, all files starting with './' should be placed on top of the filelist.f.
    # However in the latest version, they were placed at the bottom, which breaks the verilator.
    # Here is an disgusting workaround to make it work. But we need to fix this issue at firtool side.
    echo "Fixing generated filelist.f"
    grep '^\./' $out/filelist.f > prefixed.f
    grep -v '^\./' $out/filelist.f > not-prefixed.f
    cat prefixed.f not-prefixed.f > $out/filelist.f
  '';

  meta.description = "All the elaborated system verilog files for ${mlirbc.elaborateTarget} with ${mlirbc.elaborateConfig} config.";
}
