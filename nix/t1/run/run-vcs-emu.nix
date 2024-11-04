{ lib, stdenvNoCC, zstd, jq, offline-checker }:
emulator:
testCase:

stdenvNoCC.mkDerivation (finalAttr: {
  name = "${testCase.pname}-vcs-result" + (lib.optionalString emulator.enableTrace "-trace");
  nativeBuildInputs = [ zstd jq ];
  __noChroot = true;

  passthru.caseName = testCase.pname;

  buildCommand = ''
    mkdir -p "$out"

    emuDriverArgsArray=(
      "+t1_elf_file=${testCase}/bin/${testCase.pname}.elf"
      ${lib.optionalString emulator.enableTrace "+t1_wave_path=${testCase.pname}.fsdb"}
      "-cm assert"
      "-assert global_finish_maxfail=10000"
    )
    emuDriverArgs="''${emuDriverArgsArray[@]}"
    emuDriver="${emulator}/bin/${emulator.mainProgram}"

    rtlEventOutPath="$out/${testCase.pname}-rtl-event.jsonl"

    echo "[nix] Running VCS ${testCase.pname} with args $emuDriverArgs"

    printError() {
      echo -e "\033[0;31m[nix]\033[0m: online driver run failed"
      cat $rtlEventOutPath
      echo -e "\033[0;31m[nix]\033[0m: Try rerun with '\033[0;34m$emuDriver $emuDriverArgs\033[0m'"
      exit 1
    }

    export RUST_BACKTRACE=full

    "$emuDriver" $emuDriverArgs 1> >(tee $out/online-drive-emu-journal) 2>$rtlEventOutPath || printError

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
      "ERROR"
    )
    offlineCheckArgs="''${offlineCheckArgsArray[@]}"
    echo -e "[nix] running offline check: \033[0;34m${emulator}/bin/offline $offlineCheckArgs\033[0m"
    "${offline-checker}/bin/offline" $offlineCheckArgs &> >(tee $out/offline-check-journal)

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

    if [ -r perf.json ]; then
      mv perf.json $out/
    fi

    cp -v cm.log "$out"
    cp -vr cm.vdb "$out"

    ${lib.optionalString emulator.enableTrace ''
      cp -v ${testCase.pname}.fsdb "$out"
      cp -vr ${emulator}/lib/${emulator.mainProgram}.daidir "$out"
    ''}
  '';
})
