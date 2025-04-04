{
  lib,
  # build deps
  dockerTools,
  makeWrapper,
  runCommand,

  # Runtime deps
  bashInteractive,
  which,
  jq,
  coreutils,
  findutils,
  diffutils,
  gnused,
  gnugrep,
  gnutar,
  gawk,
  gzip,
  bzip2,
  gnumake,
  patch,
  xz,
  file,
  cmake,

  # Doc deps
  stdenvNoCC,
  typst,
  pandoc,

  # T1 Stuff
  rv32-stdenv,
  emurt,
  rtlDesignMetadata,
  verilator-emu,
  cases,
  configName,
}:

let
  selectedCases = with cases; [
    intrinsic.matmul
    intrinsic.linear_normalization
    asm.strlen
    disp.simple
  ];

  NIX_CFLAGS_COMPILE =
    let
      march = lib.pipe rtlDesignMetadata.march [
        (lib.splitString "_")
        (map (ext: if ext == "zvbb" then "zvbb1" else ext))
        (lib.concatStringsSep "_")
      ];
    in
    toString (
      [
        "-I${emurt}/include"
        "-L${emurt}/lib"
        "-mabi=ilp32f"
        "-march=${march}"
        "-mno-relax"
        "-static"
        "-mcmodel=medany"
        "-fvisibility=hidden"
        "-fno-PIC"
        "-g"
        "-O3"
      ]
      ++ lib.optionals (lib.elem "zvbb" (lib.splitString "_" rtlDesignMetadata.march)) [
        "-menable-experimental-extensions"
      ]
    );

  t1-cc =
    let
      cc-prefix-safe = lib.replaceStrings [ "-" ] [ "_" ] rv32-stdenv.targetPlatform.config;
    in
    runCommand "${rv32-stdenv.cc.pname}-${rv32-stdenv.cc.version}-t1-wrapped"
      {
        nativeBuildInputs = [ makeWrapper ];
        env = { inherit NIX_CFLAGS_COMPILE; };
      }
      ''
        mkdir -p $out/bin
        makeWrapper ${rv32-stdenv.cc}/bin/${rv32-stdenv.targetPlatform.config}-cc $out/bin/t1-cc \
          --set "NIX_CFLAGS_COMPILE_${cc-prefix-safe}" "$NIX_CFLAGS_COMPILE"
        makeWrapper ${rv32-stdenv.cc}/bin/${rv32-stdenv.targetPlatform.config}-c++ $out/bin/t1-c++ \
          --set "NIX_CFLAGS_COMPILE_${cc-prefix-safe}" "$NIX_CFLAGS_COMPILE"
      '';

  manual = (import ./doc.nix) {
    inherit
      lib
      typst
      pandoc
      stdenvNoCC
      ;
  };
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
    t1-cc
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
    cmake
  ];

  enableFakechroot = true;
  fakeRootCommands = ''
    echo "Start finalizing rootfs"
    mkdir -p /tmp

    echo "Creating testcase directory"
    mkdir -p /workspace/examples
    ${lib.concatStringsSep "\n" (
      # I don't know why I can't cp -r here. I can only workaround these permission issue with mkdir myself.
      map (drv: ''
        mkdir -p /workspace/examples/${drv.pname}
        cp ${drv.src}/* /workspace/examples/${drv.pname}/
      '') selectedCases
    )}

    mkdir -p /workspace/share
    cp ${../../../tests/t1.ld} /workspace/share/t1.ld
    cp ${../../../tests/t1_main.S} /workspace/share/main.S

    cp ${manual}/manual.md /workspace/readme.md
  '';

  config = {
    WorkingDir = "/workspace";
  };

  passthru = {
    inherit t1-cc manual;
  };
}
