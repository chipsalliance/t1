{ lib, stdenvNoCC, zstd, jq, offline-checker, snps-fhs-env }:

{ emulator
, dpilib ? null
}:

testCase:

assert lib.assertMsg (!emulator.isRuntimeLoad || (dpilib != null)) "dpilib must be set for rtlink emu";

stdenvNoCC.mkDerivation (finalAttr: {
  name = "${testCase.pname}-vcs-result" + (lib.optionalString emulator.enableTrace "-trace");
  nativeBuildInputs = [ zstd jq ];
  __noChroot = true;

  passthru.caseName = testCase.pname;

  buildCommand = ''
    mkdir -p "$out"

    emuDriverArgsArray=(
      ${lib.optionalString emulator.isRuntimeLoad "-sv_root ${dpilib}/lib -sv_lib ${dpilib.svLibName}"}
      "-exitstatus"
      "+t1_elf_file=${testCase}/bin/${testCase.pname}.elf"
      ${lib.optionalString emulator.enableTrace "+t1_wave_path=${testCase.pname}.fsdb"}
      ${lib.optionalString emulator.enableCover "-cm assert"}
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

    ${lib.optionalString emulator.enableCover ''
      ${snps-fhs-env}/bin/snps-fhs-env -c "urg -dir cm.vdb -format text -metric assert -show summary"
      # TODO: add a flag to specify 'vdb only generated in ci mode'
      cp -vr cm.vdb $out/
      cp -vr urgReport $out/
    ''}

    ${lib.optionalString emulator.enableTrace ''
      cp -v ${testCase.pname}.fsdb "$out"
    ''}
  '';
})
