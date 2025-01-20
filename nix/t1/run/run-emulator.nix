{ lib
, stdenvNoCC
, zstd
, jq
, offline-checker
, writeShellScriptBin
, python3
}:

{ emulator
, emuDriverArgs
, dpilib ? null
, testCase
, ...
}@args:

# if emulator doesn't have `isRuntimeLoad` attribute, then we don't need to worry about linking, quit the assertion.
assert lib.assertMsg ((!(emulator ? isRuntimeLoad)) || (!emulator.isRuntimeLoad || (dpilib != null))) "dpilib must be set for rtlink emu";

let
  overrides = builtins.removeAttrs args [ "emulator" "emuDriver" "dpilib" "testCase" ];
in
stdenvNoCC.mkDerivation (lib.recursiveUpdate
{
  name = "${testCase.pname}-vcs-result" + (lib.optionalString emulator.enableTrace "-trace");
  nativeBuildInputs = [
    zstd
    jq
    python3
  ];

  __noChroot = true;
  dontUnpack = true;

  emuDriverArgs = assert lib.assertMsg (!(emulator ? enableProfile && emulator.enableProfile)) "The provided emulator has `profile` feature enable, which is inherently nondetermistic, use '.<...attr path...>.profile --impure' instead";
    lib.escapeShellArgs emuDriverArgs;

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

  offlineCheckArgs = toString [
    "--elf-file"
    "${testCase}/bin/${testCase.pname}.elf"
    "--log-file"
    "$rtlEventOutPath"
    "--log-level"
    "ERROR"
  ];

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

    offlineCheckPhase="${lib.getExe offline-checker} $offlineCheckArgs"
    echo -e "[nix] running offline check '$offlineCheckPhase'"
    if eval "$offlineCheckPhase" &> >(tee $out/offline-check-journal); then
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

    runHook postInstall
  '';
}
  overrides)
