{
  lib,
  stdenv,
  verilator,
  snps-fhs-env,
  zlib,
}:

{
  mainProgram,
  topModule,
  rtl,
  vsrc,
  enableTrace ? false,
  extraVerilatorArgs ? [ ],
  dpiLibs ? [ ],
}:

assert lib.assertMsg (builtins.typeOf vsrc == "list") "vsrc should be a list of file path";

let
  verilatorFilelist = "${rtl}/filelist.f";
  verilatorThreads = 4;
  verilatorArgs =
    [
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
    ++ lib.optionals enableTrace [
      "+define+T1_ENABLE_TRACE"
      "--trace-fst"
    ]
    ++ extraVerilatorArgs;

  # verilatedLib should NOT depend on dpiLibs
  # to enable better caching
  verilatedLib = stdenv.mkDerivation (rec {
    name = mainProgram + "-vlib";

    __noChroot = true;

    dontUnpack = true;

    nativeBuildInputs = [ verilator ];

    # zlib is required for Rust to link against
    propagatedBuildInputs = [ zlib ];

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
    };

    installPhase = ''
      runHook preInstall

      mkdir -p $out/{include,lib}
      cp obj_dir/*.h $out/include
      cp obj_dir/*.a $out/lib

      runHook postInstall
    '';

    # nix fortify hardening add `-O2` gcc flag,
    # we'd like verilator to controll optimization flags, so disable it.
    # `-O2` will make gcc build time in verilating extremely long
    hardeningDisable = [ "fortify" ];
  });

  verilatorLinkArgs =
    [
      "${verilatedLib}/lib/libV${verilatedLib.topModule}.a"
    ]
    ++ dpiLibs
    ++ [
      "${verilatedLib}/lib/libverilated.a"
      "-lz"
    ];

  self = stdenv.mkDerivation {
    name = mainProgram;
    inherit mainProgram;

    dontUnpack = true;

    propagatedBuildInputs = [ zlib ];

    buildCommand = ''
      mkdir -p $out/bin

      $CXX -o $out/bin/$mainProgram ${lib.escapeShellArgs verilatorLinkArgs}
    '';

    meta = {
      inherit mainProgram;
    };

    passthru = {
      inherit verilatedLib;
      inherit enableTrace;
      emuKind = "verilator";
      driverWithArgs = [ (lib.getExe self) ];
    };
  };
in
self
