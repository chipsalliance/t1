{ stdenvNoCC, jq, rv32-clang, rv32-newlib, llvmForDev }:

{ caseName, compileFlags ? [ ], xLen ? 32, vLen ? 1024, fp ? false, ... }@inputs:
stdenvNoCC.mkDerivation ({
  name = "${caseName}-asm";

  unpackPhase = ''
    if [ -z "''${srcs:-}" ]; then
        if [ -z "''${src:-}" ]; then
            echo 'variable $src or $srcs should point to the source'
            exit 1
        fi
        srcs="$src"
    fi
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

  nativeBuildInputs = [
    jq
    rv32-clang
    llvmForDev.bintools
  ];

  buildPhase = ''
    runHook preBuild

    clang-rv32 $compileFlags $srcs -o $name.elf

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
