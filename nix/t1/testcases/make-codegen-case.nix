{ lib, stdenvNoCC, rvv-codegen, rv32-clang, llvmForDev }:

{ caseName, xLen ? 32, vLen ? 1024, fp ? false, compileFlags ? [ ], ... }@inputs:

stdenvNoCC.mkDerivation ({
  name = "${caseName}-codegen";
  dontUnpack = true;

  nativeBuildInputs = [ rvv-codegen rv32-clang llvmForDev.bintools ];

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
    "-I${rvv-codegen}/include"
  ];

  buildPhase = ''
    single \
      -VLEN ${toString vLen} \
      -XLEN ${toString xLen} \
      -configfile ${rvv-codegen}/configs/$caseName.toml \
      -outputfile $caseName.S

    clang-rv32 $caseName.S $compileFlags -o $caseName.elf
  '';

  installPhase = ''
    mkdir -p $out/bin

    cp $caseName.elf $out/bin
  '';
} // inputs)
