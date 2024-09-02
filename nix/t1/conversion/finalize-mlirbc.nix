{ lib
, stdenvNoCC
, circt
}:

{ mlirbc
, outputName
, ...
}@overrides:

stdenvNoCC.mkDerivation (lib.recursiveUpdate
rec {
  name = outputName;

  nativeBuildInputs = [ circt ];

  passthru = {
    last-stage-mlirbc = mlirbc;
  };

  loweringArgs = [
    "--emit-bytecode"
    "-O=debug"
    "--preserve-values=named"
    "--lowering-options=verifLabels"
  ];

  buildCommand = ''
    mkdir -p $out

    firtoolArgs="firtool ${mlirbc}/${mlirbc.name} ${lib.escapeShellArgs loweringArgs} --output-final-mlir=$out/$name"
    echo "[nix] running final lowring for MLIRBC with args: $firtoolArgs"
    $builder -c "$firtoolArgs" >/dev/null
  '';
}
  overrides)
