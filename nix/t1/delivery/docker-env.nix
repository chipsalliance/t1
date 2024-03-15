{ lib
  # build deps
, dockerTools
, runCommand

  # Runtime deps
, bashInteractive
, which
, stdenv
, openssh
, curl
, openssl
, python3
, fake-nss
, jq
, addGHUserKey

  # T1 Stuff
, elaborateConfigJson
, rv32-gcc
, ip-emulator
, soc-simulator
, subsystem-rtl
, demo-project
, testCases
}:

let
  # Don't use buildImage which relies on KVM feature
  self = dockerTools.streamLayeredImage {
    name = "chipsalliance/t1";
    tag = "latest";

    contents = with dockerTools; [
      usrBinEnv
      binSh
      fake-nss
      caCertificates
      openssl
      bashInteractive
      which
      openssh
      curl
      addGHUserKey
      soc-simulator
      ip-emulator
      rv32-gcc
    ]
    ++ stdenv.initialPath;

    enableFakechroot = true;
    fakeRootCommands = ''
      echo "Start finalizing rootfs"

      echo "Copying demo project"
      cp -r ${demo-project} /workspace/demo

      echo "Post fixup system"
      ${openssh}/bin/ssh-keygen -A

      rm /etc/ssh/sshd_config
      mkdir -p /etc/ssh/authorized_keys.d
      tee -a /etc/ssh/sshd_config <<EOF
      AuthorizedPrincipalsFile none
      GatewayPorts no
      KbdInteractiveAuthentication yes
      LogLevel INFO
      PasswordAuthentication yes
      PermitRootLogin prohibit-password
      Banner none
      Port 22
      PrintMotd yes
      AuthorizedKeysFile %h/.ssh/authorized_keys /etc/ssh/authorized_keys.d/%u
      HostKey /etc/ssh/ssh_host_rsa_key
      HostKey /etc/ssh/ssh_host_ed25519_key
      EOF
    '';

    config = {
      Cmd = [ "/bin/sshd" "-D" ];
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
