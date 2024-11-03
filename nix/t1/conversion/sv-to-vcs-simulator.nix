{ lib
, bash
, stdenv
, snps-fhs-env
}:

{ mainProgram
, rtl
, vsrc
, enableTrace ? false
, vcsLinkLibs ? [ ]
, topModule ? null
}:

assert lib.assertMsg (builtins.typeOf vsrc == "list") "vsrc should be a list of file path";
assert lib.assertMsg (builtins.typeOf vcsLinkLibs == "list") "vcsLinkLibs should be list of strings";
assert lib.assertMsg (builtins.length vcsLinkLibs > 0) "vcsLinkLibs should contain at least one static library path to link";

stdenv.mkDerivation rec {
  name = mainProgram;
  inherit mainProgram;

  # require license
  __noChroot = true;
  dontPatchELF = true;
  enableCover = true;

  dontUnpack = true;

  vcsArgs = [
    "-sverilog"
    "-full64"
    "-timescale=1ns/1ps"
    "-y"
    "$DWBB_DIR/sim_ver"
    "+libext+.v"
    "-F"
    "${rtl}/filelist.f"
  ]
  ++ vsrc
  ++ lib.optionals (topModule != null) [
    "-top"
    topModule
  ]
  ++ lib.optionals enableCover [
    "-cm"
    "line+cond+fsm+tgl+branch+assert"
    "-cm_dir"
    "./cm"
  ]
  ++ lib.optionals enableTrace [
    "+define+T1_ENABLE_TRACE"
    "-debug_access+pp+dmptf+thread"
    "-kdb=common_elab,hgldd_all"
    "-assert"
    "disable_cover"
  ]
  ++ vcsLinkLibs;

  buildPhase = ''
    runHook preBuild

    fhsEnv="${snps-fhs-env}/bin/snps-fhs-env"
    DWBB_DIR=$($fhsEnv -c "echo \$DWBB_DIR")
    vcsArgsStr="${lib.escapeShellArgs vcsArgs}"

    echo "[nix] running VCS with args: $fhsEnv vcs $vcsArgsStr"
    "$fhsEnv" -c "vcs $vcsArgsStr -o $mainProgram"

    runHook postBuild
  '';

  passthru = {
    inherit snps-fhs-env enableTrace;
  };

  shellHook = ''
    echo "[nix] entering fhs env"
    ${snps-fhs-env}/bin/snps-fhs-env
  '';

  installPhase = ''
    runHook preInstall

    mkdir -p $out/bin $out/lib
    cp $mainProgram $out/lib
    cp -r $mainProgram.daidir $out/lib
    ${lib.optionalString enableCover ''
      cp -r ./cm.vdb $out/lib
    ''}

    # We need to carefully handle string escape here, so don't use makeWrapper
    tee $out/bin/$mainProgram <<EOF
    #!${bash}/bin/bash
    export LD_LIBRARY_PATH="$out/lib/$mainProgram.daidir:\$LD_LIBRARY_PATH"
    _argv="\$@"

    ${lib.optionalString enableCover ''
      cp -r $out/lib/cm.vdb ./cm.vdb
      chmod +w -R ./cm.vdb
    ''}
    ${snps-fhs-env}/bin/snps-fhs-env -c "$out/lib/$mainProgram ${lib.optionalString enableCover ''-cm_dir ./cm.vdb''} \$_argv"
    EOF
    chmod +x $out/bin/$mainProgram

    runHook postInstall
  '';

  meta = {
    inherit mainProgram;
  };
}
