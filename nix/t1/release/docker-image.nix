{ lib
  # build deps
, dockerTools

  # Runtime deps
, bashInteractive
, which
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
  selectedCases = with cases; [
    intrinsic.matmul
    intrinsic.linear_normalization
    asm.strlen
    disp.simple
  ];
in

dockerTools.streamLayeredImage {
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
    jq
  ];

  enableFakechroot = true;
  fakeRootCommands = ''
    echo "Start finalizing rootfs"
    mkdir -p /tmp

    echo "Creating testcase directory"
    mkdir -p /workspace/examples
    ${map (drv: "cp -r ${drv.src} /workspace/example/${drv.caseName}") selectedCases}
    mkdir -p /workspace/share
  '';

  config = {
    WorkingDir = "/workspace";
  };
}
