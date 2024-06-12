# CallPackage args
{ runCommand
, t1-script
, ip-emu
, elaborateConfigJson
}:

# makeEmuResult arg
testCase:

runCommand "get-emu-result" { } ''
  ${t1-script}/bin/t1-helper \
    "ipemu" \
    --emulator-path ${ip-emu}/bin/emulator \
    --config ${elaborateConfigJson} \
    --case ${testCase}/bin/${testCase.pname}.elf \
    --no-console-logging \
    --no-file-logging \
    --out-dir $out
''
