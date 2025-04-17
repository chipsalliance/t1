# args from scope `casesSelf`
{
  stdenv,
  lib,
  jq,
  rtlDesignMetadata,
}:

# args from makeBuilder
{ casePrefix }:

# args from builder
{
  caseName,
  ...
}@overrides:

let
  # avoid adding jq to buildInputs, since it will make overriding buildInputs more error prone
  jqBin = "${jq}/bin/jq";

  caseDrv = stdenv.mkDerivation (
    self:
    lib.recursiveUpdate rec {
      # don't set name directory, since it will be suffixed with target triple
      pname = "${casePrefix}.${caseName}";
      name = pname;

      CC = "${stdenv.targetPlatform.config}-cc";
      CXX = "${stdenv.targetPlatform.config}-c++";

      NIX_CFLAGS_COMPILE =
        let
          march = lib.pipe rtlDesignMetadata.march [
            (lib.splitString "_")
            (map (
              ext:
              # g impls d
              if ext == "rv32gc" then
                "rv32imafc"
              # zvbb has experimental compiler support and required version info
              else if ext == "zvbb" then
                "zvbb1"
              else
                ext
            ))
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
          # disable the support for the Run-Time Type Information, for example, `dynamic_cast`
          "-fno-rtti"
          # disable the support for C++ exceptions
          "-fno-exceptions"
          # disables thread-safe initialization of static variables within functions
          "-fno-threadsafe-statics"
        ]
        ++ lib.optionals (lib.elem "zvbb" (lib.splitString "_" rtlDesignMetadata.march)) [
          "-menable-experimental-extensions"
        ];

      installPhase = ''
        runHook preInstall

        mkdir -p $out/bin
        cp ${pname}.elf $out/bin
        if [ -f ${pname}.cover ]; then
          cp ${pname}.cover $out/
        else
          echo "-assert *" > $out/${pname}.cover
        fi

        ${jqBin} --null-input \
          --arg name ${pname} \
          --arg elfPath "$out/bin/${pname}.elf" \
          '{ "name": $name, "elf": { "path": $elfPath } }' \
          > $out/${pname}.json

        runHook postInstall
      '';

      dontFixup = true;

      passthru = {
        inherit rtlDesignMetadata;
      };
    } overrides
  ); # end of recursiveUpdate
in
caseDrv
