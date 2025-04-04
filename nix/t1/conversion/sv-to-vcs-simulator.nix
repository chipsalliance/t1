{
  lib,
  bash,
  stdenv,
  snps-fhs-env,
}:

{
  mainProgram,
  rtl,
  vsrc,
  enableCover ? false,
  enableTrace ? false,
  vcsLinkLibs ? [ ],
  rtLinkDpiLib ? null,
  topModule ? null,
}:

assert lib.assertMsg (builtins.typeOf vsrc == "list") "vsrc should be a list of file path";
assert lib.assertMsg (
  builtins.typeOf vcsLinkLibs == "list"
) "vcsLinkLibs should be list of strings";

# Technically we could static link some libs and rtlink others,
# but currently we don't use it in such a way, so just assert it to catch error
assert lib.assertMsg (
  vcsLinkLibs != [ ] -> rtLinkDpiLib == null
) "vcsLinkLibs and rtLinkDpiLib are both set";

let
  # VCS simulation profiling
  # This is a debug feature, thus intentionally not exposed
  # Enable it by changing line below to 'true'
  enableProfile = false;

  vcsCompileArgs =
    [
      "-sverilog"
      "-full64"
      "-timescale=1ns/1ps"
      "-y"
      "$DWBB_DIR/sim_ver"
      "+libext+.v"
      "+define+PRINTF_FD=t1_common_pkg::log_fd"
    ]
    # vsrc may define sv packages, put it at the first
    ++ vsrc
    ++ [
      "-F"
      "${rtl}/filelist.f"
    ]
    ++ lib.optionals (topModule != null) [
      "-top"
      topModule
    ]
    ++ lib.optionals enableCover [
      "-cm"
      "assert"
      "-cm_dir"
      "./cm"
      "-assert"
      "enable_hier"
    ]
    ++ lib.optionals (!enableCover) [
      "-assert"
      "disable_cover"
    ]
    ++ lib.optionals enableTrace [
      "+define+T1_ENABLE_TRACE"
      "-debug_access+pp+dmptf+thread"
      "-kdb=common_elab,hgldd_all"
    ]
    ++ lib.optionals enableProfile [
      "-simprofile"
    ]
    ++ vcsLinkLibs;

  vcsRtLinkArgs = lib.optionals (rtLinkDpiLib != null) [
    "-sv_root"
    "${rtLinkDpiLib}/lib"
    "-sv_lib"
    "${rtLinkDpiLib.svLibName}"
  ];

  # vcsRtLinkArgs is only allowed in passthru
  # to enable better caching
  self = stdenv.mkDerivation {
    name = mainProgram;
    inherit mainProgram;

    # require license
    __noChroot = true;
    dontPatchELF = true;

    dontUnpack = true;

    buildPhase = ''
      runHook preBuild

      fhsEnv="${snps-fhs-env}/bin/snps-fhs-env"
      DWBB_DIR=$($fhsEnv -c "echo \$DWBB_DIR")
      vcsArgsStr="${lib.escapeShellArgs vcsCompileArgs}"

      echo "[nix] running VCS with args: $fhsEnv vcs $vcsArgsStr"
      "$fhsEnv" -c "vcs $vcsArgsStr -o $mainProgram"

      runHook postBuild
    '';

    passthru = {
      inherit snps-fhs-env enableTrace enableCover;

      emuKind = "vcs";
      driverWithArgs =
        [ (lib.getExe self) ]
        ++ vcsRtLinkArgs
        ++ [
          "-exitstatus"
          "-assert"
          "global_finish_maxfail=10000"
        ];
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
  };
in
self
