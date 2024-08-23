{ stdenvNoCC
, lib

, circt
, mlirbc
}:

let
  mfcArgs = lib.escapeShellArgs [
    "-O=debug"
    "--split-verilog"
    "--preserve-values=named"
    "--lowering-options=verifLabels,omitVersionComment"
    "--strip-debug-info"
  ];
in
stdenvNoCC.mkDerivation {
  name = "t1rocket-rtl";
  nativeBuildInputs = [ circt ];

  buildCommand = ''
    mkdir -p $out

    firtool ${mlirbc} ${mfcArgs} -o $out

    # FIXME: https://github.com/llvm/circt/pull/7543
    echo "Fixing generated filelist.f"
    pushd $out
    find . -mindepth 1 -name '*.sv' -type f > $out/filelist.f
    popd
  '';
}
