{ lib
, stdenvNoCC
, zstd
, jq
, offline-checker
, snps-fhs-env
, writeShellScriptBin
, python3
, runCoverHook
}:

{ emulator
, dpilib ? null
}:

testCase:

assert lib.assertMsg (!emulator.isRuntimeLoad || (dpilib != null)) "dpilib must be set for rtlink emu";

stdenvNoCC.mkDerivation (rec {
  name = "${testCase.pname}-vcs-result" + (lib.optionalString emulator.enableTrace "-trace");
  nativeBuildInputs = [
    zstd
    jq
    python3
  ] ++ lib.optionals (emulator.enableCover) [
    runCoverHook
    snps-fhs-env
  ];
  __noChroot = true;

  passthru = {
    # to open 'profileReport.html' in firefox,
    # set 'security.fileuri.strict_origin_policy = false' in 'about:config'
    profile = writeShellScriptBin "runSimProfile" ''
      ${lib.getExe emulator} \
        ${lib.escapeShellArgs emuDriverArgs} \
        -simprofile time \
        2> ${testCase.pname}-rtl-event.jsonl
    '';
  };

  caseName = testCase.pname;
  emuDriverArgs =
    assert lib.assertMsg (emulator ? enableProfile && emulator.enableProfile)
      "ERROR: emulator provided has `profile` feature enable, \
        which is inherently nondetermistic, use '${name}.profile --impure' instead";
    lib.escapeShellArgs (lib.optionals emulator.isRuntimeLoad [
      "-sv_root"
      "${dpilib}/lib"
      "-sv_lib"
      "${dpilib.svLibName}"
    ]
    ++ [
      "-exitstatus"
      "-assert"
      "global_finish_maxfail=10000"
      "+t1_elf_file=${testCase}/bin/${testCase.pname}.elf"
    ]
    ++ lib.optionals emulator.enableCover [
      "-cm"
      "assert"
      "-assert"
      "hier=${testCase}/${testCase.pname}.cover"
    ]
    ++ lib.optionals emulator.enableTrace [
      "+t1_wave_path=${testCase.pname}.fsdb"
    ]);

  offlineCheckArgs = lib.escapeShellArgs ([
    "--elf-file"
    "${testCase}/bin/${testCase.pname}.elf"
    "--log-file"
    "$rtlEventOutPath"
    "--log-level"
    "ERROR"
  ]);

  buildPhase = ''
    runHook preBuild

    mkdir -p "$out"

    fatal() {
      local msg="$1"; shift
      printf "\033[0;31m[nix]\033[0m: $msg\n" "$@" >&2
      exit 1
    }

    echo "[nix] Running VCS for $caseName with args $emuDriverArgs"
    export RUST_BACKTRACE=full
    rtlEventOutPath="$out/$caseName-rtl-event.jsonl"

    if ! "${lib.getExe emulator}" $emuDriverArgs \
      1> >(tee $out/online-drive-emu-journal) \
      2>$rtlEventOutPath
    then
      fatal "online driver run failed: %s\n\n%s" \
        "$(cat "$rtlEventOutPath")" \
        "Try rerun with '$emuDriver $emuDriverArgs'"
    fi

    echo "[nix] $name run done"

    if [ ! -r "$rtlEventOutPath" ]; then
      fatal "no $rtlEventOutPath found in output"
    fi

    if ! jq --stream -c -e '.[]' "$rtlEventOutPath" >/dev/null 2>&1; then
      fatal "invalid JSON file $rtlEventOutPath"
    fi

    echo -e "[nix] running offline check"
    if "${lib.getExe offline-checker}" $offlineCheckArgs &> >(tee $out/offline-check-journal); then
      printf '0' > $out/offline-check-status
      echo "[nix] Offline check PASS"
    else
      printf '1' > $out/offline-check-status
      echo "[nix] Offline check FAIL"
    fi

    echo "[nix] compressing event log"
    zstd $rtlEventOutPath -o $rtlEventOutPath.zstd
    rm $rtlEventOutPath

    runHook postBuild
  '';

  installPhase = ''
    runHook preInstall

    if [ -r perf.json ]; then
      cp -v perf.json $out/
    fi

    # If we find the mmio-event.jsonl file, try to replace the perf total cycle with program instrument.
    if [ -r mmio-event.jsonl ]; then
      cp -v mmio-event.jsonl $out/
      jq ".profile_total_cycles=$(python3 ${./calculate-cycle.py})" perf.json > "$out/perf.json"
    fi

    ${lib.optionalString emulator.enableTrace ''
      if [ -r "${testCase.pname}.fsdb" ]; then
        cp -v "${testCase.pname}.fsdb" "$out"
      fi
    ''}

    runHook postInstall
  '';
})
