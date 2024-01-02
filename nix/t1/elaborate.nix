{ stdenvNoCC
, lib
, jq

, espresso
, circt

, riscv-opcodes-src
, elaborate-config
, elaborator
, config-name
, target
}:

assert lib.assertMsg
  (lib.any (x: x == target)
    [ "ip" "ipemu" "subsystem" "subsystememu" "fpga" ]) "Unknown elaborate target ${target}";

let
  mfcArgs = lib.escapeShellArgs [
    "-O=debug"
    "--split-verilog"
    "--preserve-values=named"
    "--output-annotation-file=mfc.anno.json"
    "--lowering-options=verifLabels"
  ];
  elaborateArgs = [
    "--ip-config"
    # Can't use `toString` here, or due to some shell escape issue, Java nio cannot find the path
    "${elaborate-config}"
    "--target-dir"
    "elaborate"
  ] ++ lib.optionals (lib.elem target [ "subsystem" "subsystememu" "fpga" ]) [ "--rvopcodes-path" "${riscv-opcodes-src}" ];
in
stdenvNoCC.mkDerivation {
  name = "t1-${config-name}-elaborate-${target}";
  nativeBuildInputs = [ jq espresso circt ];
  buildCommand = ''
    mkdir -p elaborate $out
    ${elaborator}/bin/elaborator ${target} ${lib.escapeShellArgs elaborateArgs}

    # TODO: use binder to replace emiting below
    firtool elaborate/*.fir --annotation-file elaborate/*.anno.json -o $out ${mfcArgs}
  '' + lib.optionalString (lib.elem target [ "ipemu" "subsystememu" ]) ''
    # For ipemu and subsystememu, there are also some manually generated system verilog file for test bench.
    # Those files are not correctly handled by firtool.
    #
    # Fix file ordering difference introduced in some unknown breaking change between firtool 1.50 -> 1.58
    # In the previous working version, all files starting with './' should be placed on top of the filelist.f.
    # However in the latest version, they were placed at the bottom, which breaks the verilator.
    # Here is an disgusting workaround to make it work. But we need to fix this issue at firtool side.
    echo "Fixing generated filelist.f"
    grep '^\./' $out/filelist.f > prefixed.f
    grep -v '^\./' $out/filelist.f > not-prefixed.f
    cat prefixed.f not-prefixed.f > $out/filelist.f
  '';
}
