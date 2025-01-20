{ lib
, stdenv
, zlib
}:

{ mainProgram
, verilatorLib
, dpiLibs
}:

assert lib.assertMsg (builtins.typeOf dpiLibs == "list") "dpiLibs should be a list of file path";

stdenv.mkDerivation (rec {
  name = mainProgram;
  inherit mainProgram;

  dontUnpack = true;

  propagatedBuildInputs = [ zlib ];

  ccArgs = [
    "${verilatorLib}/lib/libV${verilatorLib.topModule}.a"
  ]
  ++ dpiLibs
  ++ [
    "${verilatorLib}/lib/libverilated.a"
    "-lz"
  ];

  buildCommand = ''
    mkdir -p $out/bin

    $CXX -o $out/bin/$mainProgram ${lib.escapeShellArgs ccArgs}
  '';

  passthru = {
    inherit (verilatorLib) enableTrace;
  };
})
