{ lib
, stdenv
, configName
, makeWrapper
, rtl
, vcs-dpi-lib
, vcs-fhs-env
}:

stdenv.mkDerivation {
  name = "${configName}-vcs";

  # require license
  __noChroot = true;

  src = rtl;

  nativeBuildInputs = [
    makeWrapper
  ];

  buildPhase = ''
    runHook preBuild

    echo "[nix] running VCS"
    fhsBash=${vcs-fhs-env}/bin/vcs-fhs-env
    VERDI_HOME=$("$fhsBash" -c "printenv VERDI_HOME")
    "$fhsBash" vcs \
      -sverilog \
      -full64 \
      -timescale=1ns/1ps \
      ${lib.optionalString vcs-dpi-lib.enable-trace ''
        --trace-fst \
        -P $VERDI_HOME/share/PLI/VCS/LINUX64/novas.tab $VERDI_HOME/share/PLI/VCS/LINUX64/pli.a \
        -debug_access+pp+dmptf+thread \
        -kdb=common_elab,hgldd_all''} \
      -file filelist.f \
      ${vcs-dpi-lib}/lib/libdpi.a \
      -o t1-vcs-simulator

    runHook postBuild
  '';

  passthru = {
    inherit (vcs-dpi-lib) enable-trace;
  };

  shellHook = ''
    echo "[nix] entering fhs env"
    ${vcs-fhs-env}/bin/vcs-fhs-env
  '';

  installPhase = ''
    runHook preInstall

    mkdir -p $out/bin $out/lib
    cp t1-vcs-simulator $out/lib
    cp -r t1-vcs-simulator.daidir $out/lib

    makeWrapper "${vcs-fhs-env}/bin/vcs-fhs-env" $out/bin/t1-vcs-simulator \
      --add-flags "-c $out/bin/t1-vcs-simulator" \
      --prefix LD_LIBRARY_PATH : $out/lib/t1-vcs-simulator.daidir

    runHook postInstall
  '';
}
