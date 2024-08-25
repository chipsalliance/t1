{ lib
, stdenv
, rtl
, verilator
, enable-trace ? true
, zlib
}:

stdenv.mkDerivation {
  name = "t1rocket-verilated";

  src = rtl;

  nativeBuildInputs = [ verilator ];

  propagatedBuildInputs = [ zlib ];

  buildPhase = ''
    runHook preBuild

    echo "[nix] running verilator"
    # FIXME: fix all the warning and remove -Wno-<msg> flag here
    verilator \
      ${lib.optionalString enable-trace "--trace-fst"} \
      --timing \
      --threads 8 \
      --threads-max-mtasks 8000 \
      -O1 \
      --cc TestBench

    echo "[nix] building verilated C lib"

    # backup srcs
    mkdir -p $out/share
    cp -r obj_dir $out/share/verilated_src

    rm $out/share/verilated_src/*.dat

    # We can't use -C here because VTestBench.mk is generated with relative path
    cd obj_dir
    make -j "$NIX_BUILD_CORES" -f VTestBench.mk libVTestBench

    runHook postBuild
  '';

  hardeningDisable = [ "fortify" ];

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
}
