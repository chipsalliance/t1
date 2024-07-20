{ lib
, stdenv
, configName
, makeWrapper
, rtl
, dpi-lib
, vcs-fhs-env
, enable-trace ? false
}:
stdenv.mkDerivation {
  name = "${configName}-vcs";

  # require license
  __noChroot = true;

  src = rtl;

  nativeBuildInputs = [
    vcs-fhs-env
    makeWrapper
  ];

  buildPhase = ''
    runHook preBuild

    echo "[nix] running VCS"
    ${vcs-fhs-env}/bin/vcs-fhs-env vcs \
      -sverilog \
      -full64 \
      -timescale=1ns/1ps \
      ${lib.optionalString enable-trace "--trace-fst"} \
      ${lib.optionalString enable-trace "-P $VERDI_HOME/share/PLI/VCS/LINUX64/novas.tab $VERDI_HOME/share/PLI/VCS/LINUX64/pli.a"} \
      ${lib.optionalString enable-trace "-debug_access+pp+dmptf+thread"} \
      ${lib.optionalString enable-trace "-kdb=common_elab,hgldd_all"} \
      -file filelist.f \
      ${dpi-lib}/lib/libdpi.a \
      -o t1-vcs-simulator
    runHook postBuild
  '';

  passthru = {
    inherit enable-trace;
  };

  installPhase = ''
    runHook preInstall

    mkdir -p $out/bin $out/lib $out/share/daidir
    cp t1-vcs-simulator $out/lib
    cp -r ./t1-vcs-simulator.daidir/*  $out/share/daidir

    makeWrapper $out/bin/t1-vcs-simulator --add-flags "$out/lib/t1-vcs-simulator -daidir=$out/share/daidir"
    runHook postInstall
  '';
}
