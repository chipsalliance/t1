{ lib
, stdenv
, configName
, rtl
, verilator
, enable-trace ? false
, zlib
}:
stdenv.mkDerivation {
  name = "${configName}-verilated";

  src = rtl;

  nativeBuildInputs = [ verilator ];

  # zlib is required for Rust to link against
  propagatedBuildInputs = [ zlib ];

  buildPhase = ''
    runHook preBuild

    echo "[nix] running verilator"
    verilator \
      ${lib.optionalString enable-trace "--trace-fst"} \
      --cc \
      --timing \
      --threads 8 \
      -O1 \
      -F filelist.f \
      --top TestBench

    echo "[nix] building verilated C lib"

    # backup srcs
    mkdir -p $out/share
    cp -r obj_dir $out/share/verilated_src

    # We can't use -C here because VTestBench.mk is generated with relative path
    cd obj_dir
    make -j "$NIX_BUILD_CORES" -f VTestBench.mk libVTestBench

    runHook postBuild
  '';

  passthru = {
    inherit enable-trace;
  };

  installPhase = ''
    runHook preInstall

    mkdir -p $out/include $out/lib
    cp *.h $out/include
    cp *.a $out/lib

    runHook postInstall
  '';

  # nix fortify hardening add `-O2` gcc flag,
  # we'd like verilator to controll optimization flags, so disable it.
  # `-O2` will make gcc build time in verilating extremely long
  hardeningDisable = [ "fortify" ];
}
