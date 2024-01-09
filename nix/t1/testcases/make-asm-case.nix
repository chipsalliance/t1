{ stdenv, lib, jq }:

{ caseName, xLen ? 32, vLen ? 1024, fp ? false, ... }@inputs:
stdenv.mkDerivation (rec {
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

  nativeBuildInputs = [
    jq
  ];

  buildPhase = ''
    runHook preBuild

    ${stdenv.targetPlatform.config}-cc $srcs -o ${name}.elf

    runHook postBuild
  '';

  installPhase = ''
    runHook preInstall

    mkdir -p $out/bin
    cp ${name}.elf $out/bin

    jq --null-input \
      --arg name ${caseName} \
      --arg type intrinsic \
      --argjson xLen ${toString xLen} \
      --argjson vLen ${toString vLen} \
      --argjson fp ${lib.boolToString fp} \
      --arg elfPath "$out/bin/${name}.elf" \
      '{ "name": "${name}", "type": $type, "xLen": $xLen, "vLen": $vLen, "fp": $fp, "elf": { "path": $elfPath } }' \
      > $out/${name}.json

    runHook postInstall
  '';

  meta.description = "Test case '${caseName}', written in assembly.";

  dontFixup = true;
} // inputs)
