# FIXME: we should have offline check for VCS, importing offline check from verilator-emu is weird
{ lib, stdenvNoCC, zstd, jq, verilator-emu }:
emulator:
testCase:

stdenvNoCC.mkDerivation (finalAttr: {

  name = "${testCase.pname}-vcs-result" + (lib.optionalString emulator.enable-trace "-trace");
  nativeBuildInputs = [ zstd jq ];
  __noChroot = true;

  offlineLogLevel = "ERROR";
  # Don't use true/false here, some version of nix will ignore it and cause empty env
  vcsDoLog = 0;
  passthru.debug = finalAttr.finalPackage.overrideAttrs { offlineLogLevel = "TRACE"; vcsDoLog = true; };

  buildCommand = ''
    mkdir -p "$out"

    emuDriverArgsArray=(
      "--elf-file"
      "${testCase}/bin/${testCase.pname}.elf"
      ${lib.optionalString emulator.enable-trace "--wave-path"}
      ${lib.optionalString emulator.enable-trace "${testCase.pname}.fsdb"}
    )
    emuDriverArgs="''${emuDriverArgsArray[@]}"
    emuDriver="${emulator}/bin/t1-vcs-simulator"

    rtlEventOutPath="$out/${testCase.pname}-rtl-event.jsonl"

    echo "[nix] Running VCS ${testCase.pname} with args $emuDriverArgs"

    printError() {
      echo -e "\033[0;31m[nix]\033[0m: online driver run failed"
      cat $rtlEventOutPath
      echo -e "\033[0;31m[nix]\033[0m: Try rerun with '\033[0;34m$emuDriver $emuDriverArgs\033[0m'"
      exit 1
    }

    export RUST_BACKTRACE=full

    if (( ! $vcsDoLog )); then
      "$emuDriver" $emuDriverArgs 1>/dev/null 2>$rtlEventOutPath || printError
    else
      "$emuDriver" $emuDriverArgs 1>$out/online-drive-emu-journal 2>$rtlEventOutPath || printError
    fi

    echo "[nix] VCS run done"

    if [ ! -r "$rtlEventOutPath" ]; then
      echo -e "[nix] \033[0;31mInternal Error\033[0m: no $rtlEventOutPath found in output"
      exit 1
    fi

    if ! jq --stream -c -e '.[]' "$rtlEventOutPath" >/dev/null 2>&1; then
      echo -e "[nix] \033[0;31mInternal Error\033[0m: invalid JSON file $rtlEventOutPath, showing original file:"
      echo "--------------------------------------------"
      cat $rtlEventOutPath
      echo "--------------------------------------------"
      exit 1
    fi

    set +e
    offlineCheckArgsArray=(
      "--elf-file"
      "${testCase}/bin/${testCase.pname}.elf"
      "--log-file"
      "$rtlEventOutPath"
      "--log-level"
      "$offlineLogLevel"
    )
    offlineCheckArgs="''${offlineCheckArgsArray[@]}"
    echo -e "[nix] running offline check: \033[0;34m${emulator}/bin/offline $offlineCheckArgs\033[0m"
    "${verilator-emu}/bin/offline" $offlineCheckArgs &> $out/offline-check-journal

    printf "$?" > $out/offline-check-status
    if [ "$(cat $out/offline-check-status)" != "0" ]; then
      echo "[nix] Offline check FAIL"
    else
      echo "[nix] Offline check PASS"
    fi
    set -e

    echo "[nix] compressing event log"
    zstd $rtlEventOutPath -o $rtlEventOutPath.zstd
    rm $rtlEventOutPath

    if [ -r perf.txt ]; then
      mv perf.txt $out/
    fi

    ${lib.optionalString emulator.enable-trace ''
      cp -v ${testCase.pname}.fsdb "$out"
      cp -vr ${emulator}/lib/t1-vcs-simulator.daidir "$out"
    ''}
  '';
})
