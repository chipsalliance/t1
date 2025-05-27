{
  stdenvNoCC,
  lib,
  circt,
}:

{
  outputName,
  mlirbc,
  mfcArgs,
  enableLayers,
}:

assert lib.assertMsg (builtins.typeOf mfcArgs == "list") "mfcArgs is not a list";
assert lib.assertMsg (builtins.typeOf enableLayers == "list") "enableLayers is not a list";

let
  enabledLayersDirs =
    enableLayers |> lib.map (str: "./" + lib.replaceStrings [ "." ] [ "/" ] (lib.toLower str));
in
stdenvNoCC.mkDerivation {
  name = outputName;
  nativeBuildInputs = [ circt ];

  passthru.layersDirs = enabledLayersDirs;

  buildCommand = ''
    mkdir -p $out

    firtoolArgs="firtool ${mlirbc}/${mlirbc.name} -o $out ${lib.escapeShellArgs mfcArgs}"
    echo "[nix] converting mlirbc to system verilog with args: $firtoolArgs"
    $builder -c "$firtoolArgs"

    # https://github.com/llvm/circt/pull/7543
    echo "[nix] fixing generated filelist.f"
    pushd $out >/dev/null
    find . ${lib.concatStringsSep " " enabledLayersDirs} -maxdepth 1 -name "*.sv" -type f -print > ./filelist.f
    popd >/dev/null
  '';
}
