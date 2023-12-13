{ stdenvNoCC
, lib

, espresso
, circt

, elaborate-config
, soc-elaborator
, config-name
, enableFpga ? false
}:

let
  mfcArgs = lib.escapeShellArgs [
    "-dedup"
    "-O=debug"
    "--split-verilog"
    "--preserve-values=named"
    "--output-annotation-file=mfc.anno.json"
    "--lowering-options=verifLabels"
  ];
in
stdenvNoCC.mkDerivation {
  name = "t1-soc-${config-name}-elaborate";
  nativeBuildInputs = [ espresso circt ];
  buildCommand = ''
    mkdir -p elaborate $out
    ${soc-elaborator}/bin/subsystememu --config ${elaborate-config} --dir $PWD/elaborate --riscvopcodes ${soc-elaborator}/share/riscv-opcodes --fpga ${enableFpga}

    # --disable-annotation-unknown is used for fixing "Unhandled annotation" from firtool
    firtool elaborate/*.fir --disable-annotation-unknown --annotation-file elaborate/*.anno.json -o $out ${mfcArgs}

    # Fix file ordering difference introduced in some unknown breaking change between firtool 1.50 -> 1.58
    # In the previous working version, all files starting with './' should be placed on top of the filelist.f.
    # However in the latest version, they were placed at the bottom, which breaks the verilator.
    # Here is an disgusting workaround to make it work. But we need to fix this issue at firtool side.
    grep '^\./' $out/filelist.f > prefixed.f
    grep -v '^\./' $out/filelist.f > not-prefixed.f
    cat prefixed.f not-prefixed.f > $out/filelist.f
  '';
}
