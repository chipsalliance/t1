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
, coreutils
, findutils
, diffutils
, gnused
, gnugrep
, gnutar
, gawk
, gzip
, bzip2
, gnumake
, patch
, xz
, file

  # T1 Stuff
, rv32-stdenv
, verilator-emu
, cases
, configName
}:

let
  self = dockerTools.streamLayeredImage {
    name = "chipsalliance/t1-${configName}";
    tag = "latest";

    contents = with dockerTools; [
      usrBinEnv
      binSh
      bashInteractive
      which
      rv32-stdenv.cc
      coreutils
      findutils
      diffutils
      gnused
      gnugrep
      gawk
      gnutar
      gzip
      bzip2
      gnumake
      patch
      xz
      file
      verilator-emu
    ];

    enableFakechroot = true;
    fakeRootCommands = ''
      echo "Start finalizing rootfs"
      mkdir -p /tmp

      echo "Creating testcase directory"
      mkdir -p /workspace/examples
      pushd /workspace/examples
      cp -r ${cases.intrinsic.matmul.src} .
      cp -r ${cases.intrinsic.linear_normalization.src} .
      cp -r ${cases.asm.strlen.src} .
      popd
    '';

    config = {
      WorkingDir = "/workspace";
    };
  };
in
self
