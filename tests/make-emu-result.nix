# CallPackage args
{ runCommand
, zstd
, t1-helper
, ip-emu
, elaborateConfigJson
}:

# makeEmuResult arg
testCase:

runCommand "get-${testCase.pname}-emu-result" { nativeBuildInputs = [ zstd ]; } ''
  echo "[nix] Running test case ${testCase.pname}"

  mkdir -p "$out"

  set +e
  ${t1-helper}/bin/t1-helper \
    "ipemu" \
    --emulator-path ${ip-emu}/bin/emulator \
    --config ${elaborateConfigJson} \
    --case ${testCase}/bin/${testCase.pname}.elf \
    --no-console-logging \
    --with-file-logging \
    --emulator-log-level "FATAL" \
    --emulator-log-file-path "$out/emu.log" \
    --out-dir $out &> $out/emu-wrapper.journal

  if (( $? )); then
    printf "0" > $out/emu-success
  else
    printf "1" > $out/emu-success
  fi

  if [ ! -r $out/rtl-event.log ]; then
    echo "[nix] no rtl-event.log found in output"
    echo "[nix] showing helper journal and exit"
    echo
    cat $out/emu-wrapper.journal
    exit 1
  fi

  echo "[nix] compressing event log"
  zstd $out/rtl-event.log -o $out/rtl-event.log.zstd
  rm $out/rtl-event.log

  echo "[nix] emu done"
''
