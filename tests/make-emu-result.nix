# CallPackage args
{ lib
, stdenvNoCC
, jq
, zstd

, t1rocket-emu ? null
, t1rocket-emu-trace ? null

, verilator-emu ? null
, verilator-emu-trace ? null

, vcs-emu ? null
, vcs-emu-trace ? null
}:

# makeEmuResult arg
testCase:

rec {
  verilator-check = stdenvNoCC.mkDerivation {
    name = "${testCase.pname}-emu-result";

    nativeBuildInputs = [ zstd jq ];

    dontUnpack = true;

    emuDriver = "${verilator-emu}/bin/online_drive";
    emuDriverArgs = [
      "--elf-file"
      "${testCase}/bin/${testCase.pname}.elf"
      "--log-file"
      "${placeholder "out"}/emu.log"
      "--log-level"
      "ERROR"
    ];
    rtlEventOutPath = "${placeholder "out"}/${testCase.pname}-rtl-event.jsonl";

    buildPhase = ''
      runHook preBuild

      mkdir -p "$out"

      echo "[nix] Running test case ${testCase.pname} with args $emuDriverArgs"

      export RUST_BACKTRACE=full
      if ! "$emuDriver" $emuDriverArgs 2> "$rtlEventOutPath"; then
        echo "[nix] online driver run failed"
        cat $rtlEventOutPath
        echo "[nix] Rerun with command: '$emuDriver $emuDriverArgs'"
        exit 1
      fi

      echo "[nix] online driver done"

      runHook postBuild
    '';

    doCheck = true;
    checkPhase = ''
      runHook preCheck

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

      if [ -z "$postCheck" ]; then
        set +e
        mkdir -p "$out"
        "${verilator-emu}/bin/offline" \
          --elf-file ${testCase}/bin/${testCase.pname}.elf \
          --log-file $rtlEventOutPath \
          --log-level ERROR &> $out/offline-check-journal
        printf "$?" > $out/offline-check-status
        set -e
      fi

      runHook postCheck
    '';

    installPhase = ''
      runHook preInstall

      echo "[nix] compressing event log"
      zstd $rtlEventOutPath -o $rtlEventOutPath.zstd
      rm $rtlEventOutPath

      if [ -r perf.txt ]; then
        mv perf.txt $out/
      fi

      runHook postInstall
    '';
  };

  verilator-check-trace = lib.overrideDerivation verilator-check (old: {
    name = old.name + "-with-trace";
    emuDriver = "${verilator-emu-trace}/bin/online_drive";
    emuDriverArgs = old.emuDriverArgs or [ ] ++ [ "--wave-path" "${placeholder "out"}/wave.fst" ];
    postCheck = ''
      if [ ! -r "$out/wave.fst" ]; then
      echo -e "[nix] \033[0;31mInternal Error\033[0m: waveform not found in output"
      exit 1
      fi
    '';
  });

  vcs-check = lib.overrideDerivation verilator-check (old: {
    name = old.name + "-with-vcs";
    __noChroot = true;
    dontPatchELF = true;

    buildPhase = ''
      runHook preBuild

      mkdir -p "$out"
      echo "[nix] Running VCS for ${testCase.pname}"

      RUST_BACKTRACE=full "${vcs-emu}/bin/t1-vcs-simulator" \
        --elf-file ${testCase}/bin/${testCase.pname}.elf \
        1> /dev/null \
        2> $rtlEventOutPath

      echo "[nix] VCS emu done"

      runHook postBuild
    '';

    postCheck = ''
      set +e

      "${vcs-emu}/bin/offline" \
        --elf-file ${testCase}/bin/${testCase.pname}.elf \
        --log-file $rtlEventOutPath \
        --log-level ERROR &> $out/offline-check-journal
      printf "$?" > $out/offline-check-status

      set -e
    '';
  });

  vcs-trace-check = lib.overrideDerivation verilator-check (old: {
    name = old.name + "-with-vcs-trace";
    __noChroot = true;
    dontPatchELF = true;
    buildPhase = ''
      runHook preBuild

      mkdir -p "$out"
      echo "[nix] Running VCS(TRACE) for ${testCase.pname}"

      RUST_BACKTRACE=full "${vcs-emu-trace}/bin/t1-vcs-simulator" \
        --elf-file ${testCase}/bin/${testCase.pname}.elf \
        --wave-path ${testCase.pname}.fsdb \
        1> /dev/null \
        2> $rtlEventOutPath

      echo "[nix] VCS emu done"

      runHook postBuild
    '';

    postCheck = ''
      set +e

      echo "[nix] Checking VCS event log"
      "${vcs-emu-trace}/bin/offline" \
        --elf-file ${testCase}/bin/${testCase.pname}.elf \
        --log-file $rtlEventOutPath \
        --log-level ERROR &> $out/offline-check-journal
      printf "$?" > $out/offline-check-status
      if [ "$(cat $out/offline-check-status)" == "0" ]; then
        echo "[nix] VCS difftest PASS"
      else
        echo "[nix] VCS difftest FAIL"
      fi

      set -e
    '';

    postInstall = ''
      # VCS have weird behavior on file creation, it will report read-only filesystem on our output,
      # while other tools can mutate file system correctly.
      cp ${testCase.pname}.fsdb "$out"
      cp -r ${vcs-emu-trace}/lib/t1-vcs-simulator.daidir "$out"
    '';
  });
}
