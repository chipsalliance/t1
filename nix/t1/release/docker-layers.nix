{ lib
  # build deps
, dockerTools
, runCommand

  # Runtime deps
, bashInteractive
, which
, stdenv
, jq

  # T1 Stuff
, rv32-stdenv
, emulator-wrapped
, testCases
, configName
}:

let
  # Don't use buildImage which relies on KVM feature
  self = dockerTools.streamLayeredImage {
    name = "chipsalliance/t1-${configName}";
    tag = "latest";

    contents = with dockerTools; [
      usrBinEnv
      binSh

      bashInteractive
      which

      emulator-wrapped
    ]
    ++ rv32-stdenv.initialPath;

    enableFakechroot = true;
    fakeRootCommands = ''
      echo "Start finalizing rootfs"

      echo "Creating testcase directory"
      mkdir -p /workspace/cases/
      caseArray=( ${lib.escapeShellArgs testCases} )
      for caseDir in "''${caseArray[@]}"; do
        dirName=$(${jq}/bin/jq -r '.name|split(".")|join("-")' "$caseDir"/*.json)
        cp -r "$caseDir" /workspace/cases/"$dirName"
      done
      chmod u+w -R /workspace/cases

      mkdir /tmp
    '';

    config = {
      # Cmd = [ ];
      WorkingDir = "/workspace";
    };

    passthru = {
      final-image = runCommand "convert-layer-to-final-image" { } ''
        mkdir $out

        ${bashInteractive}/bin/bash ${self} > $out/image.tar
      '';
    };
  };
in
self
