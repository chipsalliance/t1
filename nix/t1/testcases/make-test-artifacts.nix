{ lib
, runCommand
, python3
}:

testCase:

{ emuType
, elaborateConfig
, enableTrace
, emulator
}:

assert lib.assertMsg (lib.elem emuType [ "verilate" "soc" ]) "Unknown emulator type ${emuType}, required verilate or soc";

runCommand "run-${emulator.config-name}-${testCase.name}-${emuType}"
{
  nativeBuildInputs = [
    python3
  ];
}
  ''
    mkdir scripts
    cp -r ${../../../scripts/_utils.py} ./scripts/_utils.py
    cp -r ${../../../scripts/run-test.py} ./scripts/run-test.py

    python ./scripts/run-test.py ${emuType} \
      --no-file-log \
      --emulator-path "${lib.getExe emulator}" \
      --out-dir $out \
      --config-file "${elaborateConfig}" \
      ${lib.optionalString enableTrace "--trace"} \
      ${testCase}/bin/*.elf
  ''
