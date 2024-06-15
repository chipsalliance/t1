# args from scope `casesSelf`
{ stdenv
, lib
, jq
, elaborateConfig
, isFp
, vLen

  # ip-emu here is used for running the simulation to get event log for the specific test case.
, ip-emu
  # t1-script contains many decent default for running the emulator, I don't want to replicate those simulation argument here.
, t1-script
, runCommand
, elaborateConfigJson
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

  caseDrv = stdenv.mkDerivation (self: rec {
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

    passthru.emu-result = runCommand "get-event-log" { } ''
      ${t1-script}/bin/t1-helper \
        "ipemu" \
        --emulator-path ${ip-emu}/bin/emulator \
        --config ${elaborateConfigJson} \
        --case ${caseDrv}/bin/${pname}.elf \
        --no-console-logging \
        --no-file-logging \
        --out-dir $out
    '';
  } // overrides);
in
caseDrv
