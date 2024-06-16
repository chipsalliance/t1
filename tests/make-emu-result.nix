# CallPackage args
{ runCommand
, zstd
, t1-script
, ip-emu
, elaborateConfigJson
}:

# makeEmuResult arg
testCase:

runCommand "get-emu-result" { nativeBuildInputs = [ zstd ]; } ''
  echo "[NIX] Running test case ${testCase.pname}"

  mkdir -p "$out"

  set +e
  ${t1-script}/bin/t1-helper \
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

  zstd $out/rtl-event.log -o $out/rtl-event.log.zstd
  rm $out/rtl-event.log
''
