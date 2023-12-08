{ lib, writeText, jq, stdenvNoCC, rvv-codegen, rv32-clang, llvmForDev }:

{ caseName, xLen ? 32, vLen ? 1024, fp ? false, compileFlags ? [ ], ... }@inputs:
let
  caseConfigFile = writeText "${caseName}-codegen.json" (builtins.toJSON {
    name = "${caseName}";
    type = "codegen";
    inherit xLen vLen fp;
  });
in
stdenvNoCC.mkDerivation ({
  name = "${caseName}-codegen";
  dontUnpack = true;

  nativeBuildInputs = [ jq rvv-codegen rv32-clang llvmForDev.bintools ];

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

    clang-rv32 $caseName.S $compileFlags -o $name.elf
  '';

  installPhase = ''
    mkdir -p $out/bin
    cp $name.elf $out/bin
    jq ".+={\"elf\": {\"path\": \"$out/bin/$name.elf\"}}" ${caseConfigFile} > $out/$name.json
  '';
} // inputs)
