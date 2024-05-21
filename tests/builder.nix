# args from scope `casesSelf`
{ stdenv
, lib
, jq
, elaborateConfig
, isFp
, vLen
}:

# args from makeBuilder
{ casePrefix }:

# args from builder
{ caseName
, ...
} @ overrides:

let
  # avoid adding jq to buildInputs, since it will make overriding buildInputs more error prone
  jqBin = "${jq}/bin/jq";
in

stdenv.mkDerivation (self: rec {
  # don't set name directory, since it will be suffixed with target triple
  pname = "${casePrefix}.${caseName}";
  name = pname;

  CC = "${stdenv.targetPlatform.config}-cc";

  NIX_CFLAGS_COMPILE =
    let
      march = (if isFp then "rv32gc_zve32f" else "rv32gc_zve32x")
          + "_zvl${toString (lib.min 1024 vLen)}b";
    in
    [
      "-mabi=ilp32f"
      "-march=${march}"
      "-mno-relax"
      "-static"
      "-mcmodel=medany"
      "-fvisibility=hidden"
      "-fno-PIC"
      "-g"
      "-O3"
    ];

  installPhase = ''
    runHook preInstall

    mkdir -p $out/bin
    cp ${pname}.elf $out/bin

    ${jqBin} --null-input \
      --arg name ${pname} \
      --arg type ${casePrefix} \
      --arg elfPath "$out/bin/${pname}.elf" \
      '{ "name": $name, "elf": { "path": $elfPath } }' \
      > $out/${pname}.json

    runHook postInstall
  '';

  dontFixup = true;
} // overrides)
