{ lib
, stdenv
, verilator
, snps-fhs-env
, zlib
}:

{ mainProgram
, rtl
, vsrc
, enableTrace ? false
, extraVerilatorArgs ? [ ]
, topModule ? null
, ...
}@overrides:

assert lib.assertMsg (builtins.typeOf vsrc == "list") "vsrc should be a list of file path";

stdenv.mkDerivation (lib.recursiveUpdate
rec {
  name = mainProgram;
  inherit mainProgram;

  __noChroot = true;

  dontUnpack = true;

  nativeBuildInputs = [ verilator ];

  # zlib is required for Rust to link against
  propagatedBuildInputs = [ zlib ];

  verilatorFilelist = "${rtl}/filelist.f";
  verilatorThreads = 4;
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
    "-y"
    "$DWBB_DIR/sim_ver"
    "-Wno-lint"
    "-F"
    verilatorFilelist
  ]
  ++ vsrc
  ++ lib.optionals (topModule != null) [
    "--top"
    topModule
  ]
  ++ extraVerilatorArgs
  ++ lib.optionals enableTrace [
    "+define+T1_ENABLE_TRACE"
    "--trace-fst"
  ];

  buildPhase = ''
    runHook preBuild

    DWBB_DIR=$(${snps-fhs-env}/bin/snps-fhs-env -c "echo \$DWBB_DIR")

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
