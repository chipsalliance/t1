{ stdenvNoCC, buddy-mlir, rv32-clang, llvmForDev }:

{ caseName
, linkSrcs ? [ ]
, buddyOptArgs ? [ ]
, buddyTranslateArgs ? [ ]
, buddyLlcArgs ? [ ]
, compileFlags ? [ ]
, ...
}@inputs:
stdenvNoCC.mkDerivation
  ({
    name = "${caseName}-mlir";

    nativeBuildInputs = [
      buddy-mlir
      rv32-clang
      llvmForDev.bintools
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
      buddy-opt $src $buddyOptArgs -o $name-opt.mlir

      echo "Running buddy-translate with args $buddyTranslateArgs"
      buddy-translate $name-opt.mlir $buddyTranslateArgs -o $name.llvm

      echo "Running buddy-llc with args $buddyLLCArgs"
      buddy-llc $name.llvm $buddyLLCArgs --filetype=asm -o $name.S

      runHook postBuild
    '';

    compileFlags = [
      "-mabi=ilp32f"
      "-march=rv32gcv"
      "-mno-relax"
      "-static"
      "-mcmodel=medany"
      "-fvisibility=hidden"
      "-nostdlib"
      "-Wl,--entry=start"
      "-fno-PIC"
    ];

    # Set final compile and link step at postBuild, so that user can easily override them
    postBuild = ''
      clang-rv32 $compileFlags $linkSrcs $name.S -o $name.elf
    '';

    installPhase = ''
      runHook preBuild

      mkdir -p $out/bin
      cp $name.elf $out/bin/

      runHook postBuild
    '';

  } // inputs)
