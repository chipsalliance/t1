# CallPackage args
{ lib
, stdenvNoCC
, jq
, zstd
, verilator-emu
, verilator-emu-trace
, elaborateConfigJson
}:

# makeEmuResult arg
testCase:

let
  self = stdenvNoCC.mkDerivation {
    name = "${testCase.pname}-emu-result";

    nativeBuildInputs = [ zstd jq ];

    dontUnpack = true;

    difftestDriver = "${verilator-emu}/bin/online_drive";
    difftestArgs = [
      "--elf-file"
      "${testCase}/bin/${testCase.pname}.elf"
      "--log-file"
      "${placeholder "out"}/emu.log"
      "--log-level"
      "ERROR"
    ];

    buildPhase = ''
      runHook preBuild

      mkdir -p "$out"

      echo "[nix] Running test case ${testCase.pname} with args $difftestArgs"

      RUST_BACKTRACE=full "$difftestDriver" $difftestArgs 2> $out/rtl-event.jsonl

      echo "[nix] online driver done"

      runHook postBuild
    '';

    doCheck = true;
    checkPhase = ''
      runHook preCheck

      if [ ! -r $out/rtl-event.jsonl ]; then
        echo -e "[nix] \033[0;31mInternal Error\033[0m: no rtl-event.jsonl found in output"
        exit 1
      fi

      if ! jq --stream -c -e '.[]' "$out/rtl-event.jsonl" >/dev/null 2>&1; then
        echo -e "[nix] \033[0;31mInternal Error\033[0m: invalid JSON file rtl-event.jsonl, showing original file:"
        echo "--------------------------------------------"
        cat $out/rtl-event.jsonl
        echo "--------------------------------------------"
        exit 1
      fi

      runHook postCheck
    '';

    installPhase = ''
      runHook preInstall

      echo "[nix] compressing event log"
      zstd $out/rtl-event.jsonl -o $out/rtl-event.jsonl.zstd
      rm $out/rtl-event.jsonl

      mv perf.txt $out/

      runHook postInstall
    '';

    passthru.with-trace = self.overrideAttrs (old: {
      difftestDriver = "${verilator-emu-trace}/bin/online_drive";
      difftestArgs = old.difftestArgs ++ [ "--wave-path" "${placeholder "out"}/wave.fst" ];
      postCheck = ''
        if [ ! -r "$out/wave.fst" ]; then
          echo -e "[nix] \033[0;31mInternal Error\033[0m: waveform not found in output"
          exit 1
        fi
      '';
    });

    passthru.with-offline = self.overrideAttrs (old: {
      preInstall = ''
        set +e
        "${verilator-emu}/bin/offline" \
          --elf-file ${testCase}/bin/${testCase.pname}.elf \
          --log-file $out/rtl-event.jsonl \
          --log-level ERROR &> $out/offline-check-journal
        printf "$?" > $out/offline-check-status
        set -e
      '';
    });
  };
in
self
