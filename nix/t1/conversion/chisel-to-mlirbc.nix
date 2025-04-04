{
  lib,
  stdenvNoCC,

  espresso,
  circt,

  elaborator,
}:

{
  outputName,
  generatorClassName,
  elaboratorArgs,
}:

assert lib.assertMsg (builtins.typeOf elaboratorArgs == "string") "elaborateArgs should be string";
assert lib.assertMsg (
  builtins.typeOf generatorClassName == "string"
) "arg `generator` should be string";
assert lib.assertMsg (lib.hasPrefix "org.chipsalliance.t1" generatorClassName)
  "Wrong generator name ${generatorClassName}";
assert lib.assertMsg (
  !(lib.hasInfix generatorClassName elaboratorArgs)
) "Duplicated generator name in elaboratorArgs";

stdenvNoCC.mkDerivation {
  name = outputName;

  nativeBuildInputs = [
    espresso
    circt
  ];

  configGenPhase = ''
    mkdir stage1 && pushd stage1

    ${elaborator}/bin/elaborator ${generatorClassName} ${elaboratorArgs}

    popd
  '';

  elaboratePhase = ''
    mkdir stage2 && pushd stage2

    echo "[nix] Elaborating with generated config"
    ${elaborator}/bin/elaborator ${generatorClassName} design --parameter ../stage1/*.json
  '';

  buildCommand = ''
    mkdir -p "$out"

    echo "[nix] Generating config with cmd opt: $configGenPhase"
    eval "$configGenPhase"

    echo "[nix] Elaborate mlirbc with cmd opt: $elaboratePhase"
    eval "$elaboratePhase"

    echo "[nix] elaborate finish, parsing output with firtool"
    firtool *.fir \
      --annotation-file *.json \
      --emit-bytecode \
      --parse-only \
      -o $out/$name
  '';
}
