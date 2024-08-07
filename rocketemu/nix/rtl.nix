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
  name = "t1-rocketv-rtl";
  nativeBuildInputs = [ circt ];

  buildCommand = ''
    mkdir -p $out

    firtool ${mlirbc} ${mfcArgs} -o $out
  '';
}
