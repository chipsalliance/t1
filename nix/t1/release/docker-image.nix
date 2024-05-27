{ lib
  # build deps
, dockerTools
, buildEnv
, runCommand
, runtimeShell

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
  # dockerTools.buildImage relies on KVM feature, don't run it inside VMs
  self = dockerTools.buildImage rec {
    name = "chipsalliance/t1-${configName}";
    tag = "latest";

    copyToRoot = buildEnv {
      name = "${name}.imageroot";
      paths = with dockerTools; [
        usrBinEnv
        binSh

        bashInteractive
        which

        emulator-wrapped
      ] ++ rv32-stdenv.initialPath;
      pathsToLink = [ "/bin" ];
    };

    runAsRoot = ''
      #!${runtimeShell}
      echo "Start finalizing rootfs"

      echo "Creating testcase directory"
      mkdir -p /workspace/cases/
      caseArray=( ${lib.escapeShellArgs testCases} )
      for caseDir in "''${caseArray[@]}"; do
        dirName=$(${jq}/bin/jq -r '.name|split(".")|join("-")' "$caseDir"/*.json)
        cp -r "$caseDir" /workspace/cases/"$dirName"
      done
      chmod u+w -R /workspace/cases

      mkdir -p /tmp
    '';

    config = {
      # Cmd = [ ];
      WorkingDir = "/workspace";
    };
  };
in
self
