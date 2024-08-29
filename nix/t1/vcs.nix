{ lib
, callPackage
, bash
, stdenv
, configName
, vcs-emu-rtl
, vcs-dpi-lib
, vcs-fhs-env
, rtlDesignMetadata
}:

let
  self = stdenv.mkDerivation {
    name = "${configName}-vcs";

    # require license
    __noChroot = true;
    dontPatchELF = true;

    src = vcs-emu-rtl;

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
          +define+T1_ENABLE_TRACE \
          -debug_access+pp+dmptf+thread \
          -kdb=common_elab,hgldd_all''} \
        -file filelist.f \
        ${vcs-dpi-lib}/lib/libdpi_t1.a \
        -o t1-vcs-simulator

      runHook postBuild
    '';

    passthru = {
      inherit (vcs-dpi-lib) enable-trace;
      inherit vcs-fhs-env rtlDesignMetadata;

      # Here is an curry function:
      #
      #   * run-emulator.nix return type
      #   run-emulator :: { callPackage Args... } -> emulatorDerivation -> testCase -> runCommandDerivation
      #
      #   * runEmulation attribute type:
      #   runEmulation :: testCase -> runCommandDerivation
      #
      runEmulation = (callPackage ./run-vcs-emulation.nix { }) self;

      cases = callPackage ../../tests { emulator = self; };
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

      # We need to carefully handle string escape here, so don't use makeWrapper
      tee $out/bin/t1-vcs-simulator <<EOF
      #!${bash}/bin/bash
      export LD_LIBRARY_PATH="$out/lib/t1-vcs-simulator.daidir:\$LD_LIBRARY_PATH"
      _argv="\$@"
      ${vcs-fhs-env}/bin/vcs-fhs-env -c "$out/lib/t1-vcs-simulator \$_argv"
      EOF
      chmod +x $out/bin/t1-vcs-simulator

      runHook postInstall
    '';
  };
in
self
