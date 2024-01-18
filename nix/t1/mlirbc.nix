{ stdenvNoCC
, lib

, espresso
, circt

, riscv-opcodes-src
, elaborate-config
, elaborator
, configName
, target
}:

assert lib.assertMsg
  (lib.elem target [ "ip" "ipemu" "subsystem" "subsystememu" "fpga" ])
  "Unknown elaborate target ${target}";

let
  elaborateArgs = [
    "--ip-config"
    # Can't use `toString` here, or due to some shell escape issue, Java nio cannot find the path
    "${elaborate-config}/config.json"
    "--target-dir"
    "elaborate"
  ] ++ lib.optionals (lib.elem target [ "subsystem" "subsystememu" "fpga" ]) [ "--rvopcodes-path" "${riscv-opcodes-src}" ];
in
stdenvNoCC.mkDerivation {
  name = "t1-${target}-${configName}-mlirbc";

  nativeBuildInputs = [ espresso circt ];

  passthru = {
    elaborateTarget = target;
    elaborateConfig = configName;
  };

  buildCommand = ''
    mkdir -p elaborate $out

    ${elaborator}/bin/elaborator ${target} ${lib.escapeShellArgs elaborateArgs}

    firtool elaborate/*.fir \
      --annotation-file elaborate/*.anno.json \
      -O=debug \
      --preserve-values=named \
      --output-annotation-file=mfc.anno.json \
      --lowering-options=verifLabels \
      --emit-bytecode \
      --ir-verilog \
      -o $out/${target}-${configName}.mlirbc
  '';

  meta.description = "Elaborated MLIR Bytecode file for ${target} with config ${configName}.";
}
