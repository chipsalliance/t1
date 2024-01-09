{ stdenv, lib, jq, buddy-mlir }:

{ caseName
, linkSrcs ? [ ]
, buddyOptArgs ? [ ]
, buddyTranslateArgs ? [ ]
, buddyLLCArgs ? [ ]
, compileFlags ? [ ]
, xLen ? 32
, vLen ? 1024
, fp ? false
, ...
}@inputs:
stdenv.mkDerivation
  (rec {
    name = "${caseName}-mlir";

    nativeBuildInputs = [
      jq
      buddy-mlir
    ];

    dontUnpack = true;

    buddyOptArgs = [
      "--lower-affine"
      "--convert-scf-to-cf"
      "--convert-math-to-llvm"
      "--lower-vector-exp"
      "--lower-rvv=rv32"
      "--convert-vector-to-llvm"
      "--finalize-memref-to-llvm"
      "--convert-arith-to-llvm"
      "--convert-func-to-llvm"
      "--reconcile-unrealized-casts"
    ];
    buddyTranslateArgs = [ "--buddy-to-llvmir" ];
    buddyLLCArgs = [
      "-mtriple=riscv32"
      "-target-abi=ilp32f"
      "-mattr=+m,+f,+zve32f"
      "-riscv-v-vector-bits-min=128"
    ];

    buildPhase = ''
      runHook preBuild

      echo "Running buddy-opt with args $buddyOptsArgs"
      buddy-opt $src $buddyOptArgs -o ${name}-opt.mlir

      echo "Running buddy-translate with args $buddyTranslateArgs"
      buddy-translate ${name}-opt.mlir $buddyTranslateArgs -o ${name}.llvm

      echo "Running buddy-llc with args $buddyLLCArgs"
      buddy-llc ${name}.llvm $buddyLLCArgs --filetype=asm -o ${name}.S

      runHook postBuild
    '';

    NIX_CFLAGS_COMPILE = [
      "-mabi=ilp32f"
      "-march=rv32gcv"
      "-mno-relax"
      "-static"
      "-mcmodel=medany"
      "-fvisibility=hidden"
      "-nostdlib"
      "-fno-PIC"
    ];

    # Set final compile and link step at postBuild, so that user can easily override them
    postBuild = ''
      ${stdenv.targetPlatform.config}-cc $linkSrcs ${name}.S -o ${name}.elf
    '';

    installPhase = ''
      runHook preInstall

      mkdir -p $out/bin
      cp ${name}.elf $out/bin/

      set -x
      jq --null-input \
        --arg name ${caseName} \
        --arg type intrinsic \
        --argjson xLen ${toString xLen} \
        --argjson vLen ${toString vLen} \
        --argjson fp ${lib.boolToString fp} \
        --arg elfPath "$out/bin/${name}.elf" \
        '{ "name": "${name}", "type": $type, "xLen": $xLen, "vLen": $vLen, "fp": $fp, "elf": { "path": $elfPath } }' \
        > $out/${name}.json

      runHook preInstall
    '';

    meta.description = "Test case '${caseName}', written in MLIR.";

    dontFixup = true;
  } // inputs)
