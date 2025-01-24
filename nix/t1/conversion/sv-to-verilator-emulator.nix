{ lib
, stdenv
, verilator
, snps-fhs-env
, zlib
}:

{ mainProgram
, topModule
, rtl
, vsrc
, enableTrace ? false
, extraVerilatorArgs ? [ ]
}:

assert lib.assertMsg (builtins.typeOf vsrc == "list") "vsrc should be a list of file path";

stdenv.mkDerivation (rec {
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
    "--timescale"
    "1ns/1ps"
    "--timing"
    "--threads"
    (toString verilatorThreads)
    "-O1"
    "-y"
    "$DWBB_DIR/sim_ver"
    "-Wno-lint"
    "+define+PRINTF_FD=t1_common_pkg::log_fd"
  ]
  # vsrc may define sv packages, put it at the first
  ++ vsrc
  ++ [
    "-F"
    verilatorFilelist
  ]
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

    make -j "$NIX_BUILD_CORES" -f V${topModule}.mk -C obj_dir

    runHook postBuild
  '';

  passthru = {
    inherit topModule;
    inherit enableTrace;

    buildCmdArgs =
      { plusargs
      , verilatorExtraArgs ? [ ]
      }: plusargs ++ verilatorExtraArgs;
  };

  installPhase = ''
    runHook preInstall

    mkdir -p $out/{include,lib}
    cp obj_dir/*.h $out/include
    cp obj_dir/*.a $out/lib

    runHook postInstall
  '';

  meta = {
    inherit mainProgram;
  };

  # nix fortify hardening add `-O2` gcc flag,
  # we'd like verilator to controll optimization flags, so disable it.
  # `-O2` will make gcc build time in verilating extremely long
  hardeningDisable = [ "fortify" ];
})
