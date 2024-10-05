{ lib
, callPackage
, bash
, stdenv
, vcs-fhs-env
}:

{ mainProgram
, rtl
, enableTrace ? false
, vcsLinkLibs ? [ ]
}:

assert lib.assertMsg (builtins.typeOf vcsLinkLibs == "list") "vcsLinkLibs should be list of strings";
assert lib.assertMsg (builtins.length vcsLinkLibs > 0) "vcsLinkLibs should contain at least one static library path to link";

stdenv.mkDerivation rec {
  name = mainProgram;
  inherit mainProgram;

  # require license
  __noChroot = true;
  dontPatchELF = true;

  src = rtl;

  vcsArgs = [
    "-sverilog"
    "-full64"
    "-timescale=1ns/1ps"
    "-file"
    "filelist.f"
    "-cm"
    "line+cond+fsm+tgl+branch+assert"
    "-cm_dir"
    "./cm"
  ]
  ++ lib.optionals (enableTrace) [
    "+define+T1_ENABLE_TRACE"
    "-debug_access+pp+dmptf+thread"
    "-kdb=common_elab,hgldd_all"
  ]
  ++ vcsLinkLibs;

  buildPhase = ''
    runHook preBuild

    vcsArgsStr="${lib.escapeShellArgs vcsArgs}"
    fhsEnv="${vcs-fhs-env}/bin/vcs-fhs-env"

    echo "[nix] running VCS with args: $fhsEnv vcs $vcsArgsStr"
    "$fhsEnv" -c "vcs $vcsArgsStr -o $mainProgram"

    runHook postBuild
  '';

  passthru = {
    inherit vcs-fhs-env enableTrace;
  };

  shellHook = ''
    echo "[nix] entering fhs env"
    ${vcs-fhs-env}/bin/vcs-fhs-env
  '';

  installPhase = ''
    runHook preInstall

    mkdir -p $out/bin $out/lib
    cp $mainProgram $out/lib
    cp -r $mainProgram.daidir $out/lib
    cp -r ./cm.vdb $out/lib

    # We need to carefully handle string escape here, so don't use makeWrapper
    tee $out/bin/$mainProgram <<EOF
    #!${bash}/bin/bash
    export LD_LIBRARY_PATH="$out/lib/$mainProgram.daidir:\$LD_LIBRARY_PATH"
    _argv="\$@"
    cp -r $out/lib/cm.vdb ./cm.vdb
    chmod +w -R ./cm.vdb
    ${vcs-fhs-env}/bin/vcs-fhs-env -c "$out/lib/$mainProgram -cm_dir ./cm.vdb \$_argv"
    EOF
    chmod +x $out/bin/$mainProgram

    runHook postInstall
  '';

  meta = {
    inherit mainProgram;
  };
}
