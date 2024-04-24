{ stdenvNoCC
, lib

, omreader
, circt
, mlirbc
}:

let
  omReaderArgs = lib.filter (s: s != "") [
    "--mlirbc-file"
    "${mlirbc}/${mlirbc.elaborateTarget}-${mlirbc.elaborateConfig}.mlirbc"
  ];
in
stdenvNoCC.mkDerivation {
  name = "t1-${mlirbc.elaborateConfig}-${mlirbc.elaborateTarget}-om";

  nativeBuildInputs = [ circt omreader ];

  buildCommand = ''
    omreader ${lib.escapeShellArgs omReaderArgs}
  '';

  meta.description = "TODO.";
}
