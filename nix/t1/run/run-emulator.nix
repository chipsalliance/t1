{ lib
, stdenvNoCC
, zstd
, jq
, sim-checker
, writeShellScriptBin
, python3
}:

{ testCase
, emulator
, emuExtraArgs ? [ ]
, waveFileName ? null
, ...
}@args:

assert if emulator.enableTrace
then (lib.assertMsg (waveFileName != null) "waveFileName shall be set for trace build")
else (lib.assertMsg (waveFileName == null) "waveFileName must not be set for non-trace build");
let
  overrides = builtins.removeAttrs args [ "emulator" "emuExtraArgs" "testCase" "waveFileName" ];
  plusargs = [
    "+t1_elf_file=${testCase}/bin/${testCase.pname}.elf"
    "+t1_rtl_event_path=rtl-event.jsonl"
  ]
  ++ lib.optionals (waveFileName != null) [
    "+t1_wave_path=${waveFileName}"
  ];
  emuDriverWithArgs = emulator.driverWithArgs ++ plusargs ++ emuExtraArgs;
in
stdenvNoCC.mkDerivation (lib.recursiveUpdate
{
  name = "${testCase.pname}-${emulator.emuKind}-result" + (lib.optionalString emulator.enableTrace "-trace");
  nativeBuildInputs = [
    zstd
    jq
    python3
  ];

  __noChroot = true;
  dontUnpack = true;

  emuDriverWithArgs = assert lib.assertMsg (!(emulator.enableProfile or false)) "The provided emulator has `profile` feature enable, which is inherently nondetermistic, use '.<...attr path...>.profile --impure' instead";
    toString emuDriverWithArgs;

  passthru = {
    # to open 'profileReport.html' in firefox,
    # set 'security.fileuri.strict_origin_policy = false' in 'about:config'
    profile = writeShellScriptBin "runSimProfile" ''
      ${lib.escapeShellArgs emuDriverWithArgs} \
        -simprofile time
    '';
  };

  caseName = testCase.pname;

  simCheckArgs = toString [
    "--sim-result"
    "sim_result.json"
    "--rtl-event-file"
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

    driverPhase="$emuDriverWithArgs"
    echo "[nix] Running '$driverPhase'"
    export RUST_BACKTRACE=full
    rtlEventOutPath="rtl-event.jsonl"

    if ! eval "$driverPhase"  \
      &> >(tee $out/online-drive-emu-journal)
    then
      fatal "online driver run failed: %s\n\n%s" \
        "$(cat "$rtlEventOutPath")" \
        "Try rerun with '$emuDriverWithArg'"
    fi

    echo "[nix] $name run done"

    if [ ! -r "$rtlEventOutPath" ]; then
      fatal "no $rtlEventOutPath found in output"
    fi

    if ! jq --stream -c -e '.[]' "$rtlEventOutPath" >/dev/null 2>&1; then
      fatal "invalid JSON file $rtlEventOutPath"
    fi

    simCheckPhase="${lib.getExe sim-checker} $simCheckArgs"
    echo -e "[nix] running simulation check '$simCheckPhase'"
    if eval "$simCheckPhase" &> >(tee $out/sim-check-journal); then
      printf '0' > $out/sim-check-status
      echo "[nix] Simulation check PASS"
    else
      printf '1' > $out/sim-check-status
      echo "[nix] Simulation check FAIL"
    fi

    echo "[nix] compressing event log"
    zstd $rtlEventOutPath -o $out/$caseName-rtl-event.zstd
    rm $rtlEventOutPath

    runHook postBuild
  '';

  installPhase = ''
    runHook preInstall

    if [ -r sim_result.json ]; then
      cp -v sim_result.json $out/
    fi

    ${lib.optionalString (waveFileName != null) ''
      cp -v ${waveFileName} $out/
    ''}

    # If we find the mmio-event.jsonl file, try to replace the perf total cycle with program instrument.
    if [ -r mmio-event.jsonl ]; then
      cp -v mmio-event.jsonl $out/
      jq ".profile_total_cycles=$(python3 ${./calculate-cycle.py})" sim_result.json > "$out/sim_result.json"
    fi

    runHook postInstall
  '';
}
  overrides)
