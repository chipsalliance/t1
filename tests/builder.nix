# args from scope `casesSelf`
{ stdenv
, jq
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
in

stdenv.mkDerivation (self: rec {
  # don't set name directory, since it will be suffixed with target triple
  pname = "${casePrefix}.${caseName}";
  name = pname;

  CC = "${stdenv.targetPlatform.config}-cc";

  NIX_CFLAGS_COMPILE = [
    "-mabi=ilp32f"
    "-march=rv32gcv"
    "-mno-relax"
    "-static"
    "-mcmodel=medany"
    "-fvisibility=hidden"
    "-fno-PIC"
    "-g"
    "-O3"
  ];

  configurePhase = ''
    export vLen=$(${jqBin} --exit-status --raw-output ".parameter.vLen" ${elaborateConfigJson})

    is32BitLen=$(${jqBin} -r '.parameter.extensions[] | test("ve32")' ${elaborateConfigJson})
    if [[ "$is32BitLen" = "true" ]] ; then
      export xLen=32
    else
      export xLen=64
    fi

    isFp=$(${jqBin} -r '.parameter.extensions[] | test("f")' ${elaborateConfigJson})

    echo "Set vLen=$vLen xLen=$xLen isFp=$isFp"
  '';

  installPhase = ''
    runHook preInstall

    mkdir -p $out/bin
    cp ${pname}.elf $out/bin

    ${jqBin} --null-input \
      --arg name ${pname} \
      --arg type ${casePrefix} \
      --argjson xLen "$xLen" \
      --argjson vLen "$vLen" \
      --argjson fp "$isFp" \
      --arg elfPath "$out/bin/${pname}.elf" \
      '{ "name": $name, "type": $type, "xLen": $xLen, "vLen": $vLen, "fp": $fp, "elf": { "path": $elfPath } }' \
      > $out/${pname}.json

    runHook postInstall
  '';

  dontFixup = true;
} // overrides)
