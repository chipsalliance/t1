{ lib
, stdenvNoCC

, espresso
, circt

, elaborator
}:

{ outputName
, generatorClassName
, elaboratorArgs
}:

assert lib.assertMsg (builtins.typeOf elaboratorArgs == "string") "elaborateArgs should be string";
assert lib.assertMsg (builtins.typeOf generatorClassName == "string") "arg `generator` should be string";
assert lib.assertMsg (lib.hasPrefix "org.chipsalliance.t1" generatorClassName) "Wrong generator name ${generatorClassName}";
assert lib.assertMsg (!(lib.hasInfix generatorClassName elaboratorArgs)) "Duplicated generator name in elaboratorArgs";

stdenvNoCC.mkDerivation {
  name = outputName;

  nativeBuildInputs = [ espresso circt ];

  buildCommand = ''
    mkdir -p "$out"

    mkdir stage1 && pushd stage1

    elaborateProc="${elaborator}/bin/elaborator ${generatorClassName} ${elaboratorArgs}"
    echo "[nix] Generating config with cmd opt: $elaborateProc"
    eval "$elaborateProc"
    popd

    mkdir stage2 && pushd stage2
    echo "[nix] Elaborating with generated config"
    ${elaborator}/bin/elaborator ${generatorClassName} design --parameter ../stage1/*.json

    # ---------------------------------------------------------------------------------
    # Run circt toolchain
    # ---------------------------------------------------------------------------------
    echo "[nix] elaborate finish, parsing output with firtool"
    firtool *.fir \
      --annotation-file *.json \
      --emit-bytecode \
      --parse-only \
      -o $out/$name
  '';
}
