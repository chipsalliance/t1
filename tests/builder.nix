# args from scope `casesSelf`
{ stdenv
, lib
, jq
, rtlDesignMetadata

, makeEmuResult
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

  caseDrv = stdenv.mkDerivation (self: lib.recursiveUpdate
    rec {
      # don't set name directory, since it will be suffixed with target triple
      pname = "${casePrefix}.${caseName}";
      name = pname;

      CC = "${stdenv.targetPlatform.config}-cc";
      CXX = "${stdenv.targetPlatform.config}-c++";

      NIX_CFLAGS_COMPILE =
        let
          march = lib.pipe rtlDesignMetadata.march [
            (lib.splitString "_")
            (map (ext: if ext == "zvbb" then "zvbb1" else ext))
            (lib.concatStringsSep "_")
          ];
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
        ] ++ lib.optionals (lib.elem "zvbb" (lib.splitString "_" rtlDesignMetadata.march)) [ "-menable-experimental-extensions" ];

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

      passthru = {
        inherit rtlDesignMetadata;
        emu-result = makeEmuResult caseDrv;
      };
    }
    overrides); # end of recursiveUpdate
in
caseDrv
