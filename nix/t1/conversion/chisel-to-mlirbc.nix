{ lib
, stdenvNoCC

, espresso
, circt

, elaborator
}:

{ outputName
, elaboratorArgs ? null
}:

assert lib.assertMsg (elaboratorArgs != null) "elaborateArgs is not set";
assert lib.assertMsg (builtins.typeOf elaboratorArgs == "list") "elaborateArgs is not a list";
assert lib.assertMsg (!(lib.elem "--target-dir" elaboratorArgs)) "--target-dir is set internally, please remove it from top scope";

let
  elaborateArgStr = lib.escapeShellArgs elaboratorArgs;
in
stdenvNoCC.mkDerivation {
  name = outputName;

  nativeBuildInputs = [ espresso circt ];

  elaborateOutDir = "elaborate";
  buildCommand = ''
    mkdir -p "$out" "$elaborateOutDir"

    # ---------------------------------------------------------------------------------
    # Run elaborator
    # ---------------------------------------------------------------------------------
    elaborateProc="${elaborator}/bin/elaborator ${elaborateArgStr} --target-dir $elaborateOutDir"
    echo "[nix] running elaborator: $elaborateProc"
    $builder -c "$elaborateProc"

    # ---------------------------------------------------------------------------------
    # Run circt toolchain
    # ---------------------------------------------------------------------------------
    echo "[nix] elaborate finish, parsing output with firtool"
    firtool elaborate/*.fir \
      --annotation-file elaborate/*.anno.json \
      --emit-bytecode \
      --parse-only \
      -o $out/$name
  '';
}
