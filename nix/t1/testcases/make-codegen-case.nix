{ lib, jq, stdenvNoCC, rvv-codegen, rv32-clang, llvmForDev }:

{ caseName, xLen ? 32, vLen ? 1024, fp ? false, compileFlags ? [ ], ... }@inputs:
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
    "-fno-PIC"
    "-I${rvv-codegen}/include"
  ];

  buildPhase = ''
    runHook preBuild

    single \
      -VLEN ${toString vLen} \
      -XLEN ${toString xLen} \
      -configfile ${rvv-codegen}/configs/$caseName.toml \
      -outputfile $caseName.S

    clang-rv32 $caseName.S $compileFlags -o $name.elf

    runHook postBuild
  '';

  installPhase = ''
    runHook preInstall

    mkdir -p $out/bin
    cp $name.elf $out/bin

    jq --null-input \
      --arg name ${caseName} \
      --arg type intrinsic \
      --argjson xLen ${toString xLen} \
      --argjson vLen ${toString vLen} \
      --argjson fp '${if fp then "true" else "false"}' \
      --arg elfPath "$out/bin/$name.elf" \
      '{ "name": $name, "type": $type, "xLen": $xLen, "vLen": $vLen, "fp": $fp, "elf": { "path": $elfPath } }' \
      > $out/$name.json

    runHook postInstall
  '';

  dontFixup = true;
} // inputs)
