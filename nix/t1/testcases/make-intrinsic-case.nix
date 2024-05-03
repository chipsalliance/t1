{ stdenv, jq, lib, linkerScript }:

{ caseName, xLen ? 32, vLen ? 1024, fp ? false, ... }@inputs:

stdenv.mkDerivation (self: rec {
  casePrefix = "intrinsic";
  name = "${self.casePrefix}.${caseName}";

  unpackPhase = ''
    runHook preUnpack
    if [ -z "''${srcs:-}" ]; then
        if [ -z "''${src:-}" ]; then
            echo 'variable $src or $srcs should point to the source'
            exit 1
        fi
        srcs="$src"
    fi
    runHook postUnpack
  '';

  NIX_CFLAGS_COMPILE = [
    "-mabi=ilp32f"
    "-march=rv32gcv"
    "-mno-relax"
    "-static"
    "-mcmodel=medany"
    "-fvisibility=hidden"
    # "-nostdlib"
    "-fno-PIC"
    "-g"
    "-O3"

    "-T"
    "${linkerScript}"
  ];

  nativeBuildInputs = [ jq ];

  buildPhase = ''
    runHook preBuild

    ${stdenv.targetPlatform.config}-cc $srcs -o ${name}.elf
    ${stdenv.targetPlatform.config}-objdump -S ${name}.elf > ${name}.dump

    runHook postBuild
  '';

  installPhase = ''
    runHook preInstall

    mkdir -p $out/bin
    cp ${name}.elf $out/bin
    cp ${name}.dump $out/${name}.dump

    jq --null-input \
      --arg name ${caseName} \
      --arg type ${self.casePrefix} \
      --argjson xLen ${toString xLen} \
      --argjson vLen ${toString vLen} \
      --argjson fp ${lib.boolToString fp} \
      --arg elfPath "$out/bin/${name}.elf" \
      '{ "name": "${name}", "type": $type, "xLen": $xLen, "vLen": $vLen, "fp": $fp, "elf": { "path": $elfPath } }' \
      > $out/${name}.json

    runHook postInstall
  '';

  dontFixup = true;

  meta.description = "Test case '${caseName}', written in C intrinsic.";
} // inputs)
