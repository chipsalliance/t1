{ lib
, stdenv
, verilator
, zlib
}:

{ mainProgram
, rtl
, enableTrace ? false
, extraVerilatorArgs ? [ ]
, ...
}@overrides:

stdenv.mkDerivation (lib.recursiveUpdate
rec {
  name = mainProgram;
  inherit mainProgram;

  src = rtl;

  nativeBuildInputs = [ verilator ];

  # zlib is required for Rust to link against
  propagatedBuildInputs = [ zlib ];

  verilatorFilelist = "filelist.f";
  verilatorTop = "TestBench";
  verilatorThreads = 8;
  verilatorArgs = [
    "--cc"
    "--main"
    "--exe"
    "--timescale"
    "1ns/1ps"
    "--timing"
    "--threads"
    (toString verilatorThreads)
    "-O1"
    "-F"
    verilatorFilelist
    "--top"
    verilatorTop
  ]
  ++ extraVerilatorArgs
  ++ lib.optionals (enableTrace) [
    "+define+T1_ENABLE_TRACE"
    "--trace-fst"
  ];

  buildPhase = ''
    runHook preBuild

    verilatorPhase="verilator ${lib.escapeShellArgs verilatorArgs}"
    echo "[nix] running verilator: $verilatorPhase"
    $builder -c "$verilatorPhase"

    echo "[nix] building verilated C lib"
    # backup srcs
    mkdir -p $out/share
    cp -r obj_dir $out/share/verilated_src

    # We can't use -C here because VTestBench.mk is generated with relative path
    cd obj_dir
    make -j "$NIX_BUILD_CORES" -f VTestBench.mk VTestBench

    runHook postBuild
  '';

  passthru = {
    inherit enableTrace;
  };

  installPhase = ''
    runHook preInstall

    mkdir -p $out/{include,lib,bin}
    cp *.h $out/include
    cp *.a $out/lib
    cp VTestBench $out/bin/$mainProgram

    runHook postInstall
  '';

  meta = {
    inherit mainProgram;
  };

  # nix fortify hardening add `-O2` gcc flag,
  # we'd like verilator to controll optimization flags, so disable it.
  # `-O2` will make gcc build time in verilating extremely long
  hardeningDisable = [ "fortify" ];
}
  overrides)
