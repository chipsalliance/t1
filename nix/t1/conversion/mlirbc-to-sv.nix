{
  stdenvNoCC,
  lib,
  circt,
}:

{
  outputName,
  mlirbc,
  mfcArgs,
}:

assert lib.assertMsg (builtins.typeOf mfcArgs == "list") "mfcArgs is not a list";

stdenvNoCC.mkDerivation {
  name = outputName;
  nativeBuildInputs = [ circt ];

  buildCommand = ''
    mkdir -p $out

    firtoolArgs="firtool ${mlirbc}/${mlirbc.name} -o $out ${lib.escapeShellArgs mfcArgs}"
    echo "[nix] converting mlirbc to system verilog with args: $firtoolArgs"
    $builder -c "$firtoolArgs"

    # https://github.com/llvm/circt/pull/7543
    echo "[nix] fixing generated filelist.f"
    pushd $out >/dev/null
    find . -mindepth 1 -name '*.sv' -type f > $out/filelist.f
    popd >/dev/null
  '';
}
