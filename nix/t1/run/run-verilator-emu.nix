{ lib, stdenvNoCC, zstd, jq, offline-checker }:
emulator:
testCase:

stdenvNoCC.mkDerivation (finalAttr: {
  name = "${emulator.name}-${testCase.pname}-emu-result" + lib.optionalString emulator.enableTrace "-trace";
  nativeBuildInputs = [ zstd jq ];

  passthru.caseName = testCase.pname;

  buildCommand = ''
    mkdir -p "$out"

    emuDriverArgsArray=(
      "+t1_elf_file=${testCase}/bin/${testCase.pname}.elf"
      ${lib.optionalString emulator.enableTrace "+t1_wave_path=$out/wave.fst"}
    )
    emuDriverArgs="''${emuDriverArgsArray[@]}"
    emuDriver="${emulator}/bin/${emulator.mainProgram}"

    rtlEventOutPath="$out/${testCase.pname}-rtl-event.jsonl"

    echo "[nix] Running test case ${testCase.pname} with args $emuDriverArgs"

    printError() {
      echo -e "\033[0;31m[nix]\033[0m: online driver run failed"
      cat $rtlEventOutPath
      echo -e "\033[0;31m[nix]\033[0m: Try rerun with '\033[0;34m$emuDriver $emuDriverArgs\033[0m'"
      exit 1
    }

    "$emuDriver" $emuDriverArgs 1>$out/online-drive-journal 2> "$rtlEventOutPath" || printError

    echo "[nix] t1rocket run done"

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
      "info"
    )
    offlineCheckArgs="''${offlineCheckArgsArray[@]}"
    echo -e "[nix] running offline check: \033[0;34m${offline-checker}/bin/offline $offlineCheckArgs\033[0m"
    "${offline-checker}/bin/offline" $offlineCheckArgs &> $out/offline-check-journal

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

    ${lib.optionalString emulator.enableTrace ''
      if [ ! -r "$out/wave.fst" ]; then
        echo -e "[nix] \033[0;31mInternal Error\033[0m: waveform not found in output"
        exit 1
      fi
    ''}
  '';
})
