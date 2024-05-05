{ stdenvNoCC
, circt
, elaborate
}:

let
  uniqueName = "${elaborate.elaborateTarget}-${elaborate.elaborateConfig}";
in
stdenvNoCC.mkDerivation {
  name = "t1-${uniqueName}-mlirbc";

  nativeBuildInputs = [ circt ];

  inherit (elaborate) passthru;

  buildCommand = ''
    mkdir $out

    firtool ${elaborate}/${uniqueName}.mlirbc \
      --emit-bytecode \
      -O=debug \
      --preserve-values=named \
      --lowering-options=verifLabels \
      --output-final-mlir=$out/${uniqueName}-lowered.mlirbc
  '';

  meta.description = "Lowered MLIR Bytecode file (${uniqueName}).";
}
