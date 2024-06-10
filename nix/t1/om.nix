{ stdenvNoCC
, lib

, omreader
, circt
, mlirbc
}:

let
  omReaderArgs = lib.filter (s: s != "") [
    "run"
    "--mlirbc-file"
    "${mlirbc}/${mlirbc.elaborateTarget}-${mlirbc.elaborateConfig}-lowered.mlirbc"
    "--dump-methods"
  ];
in
stdenvNoCC.mkDerivation {
  name = "t1-${mlirbc.elaborateConfig}-${mlirbc.elaborateTarget}-om";

  nativeBuildInputs = [ circt omreader ];

  buildCommand = ''
    omreader ${lib.escapeShellArgs omReaderArgs} > $out
  '';

  meta.description = "Call CLI dumps OM properties from MLIR bytecodes";
}
