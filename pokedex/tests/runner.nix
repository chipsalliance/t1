{
  runCommand,
  spike,
  dtc,
  model,
  simulator,
  jq,
}:
{ caseInfo }:
runCommand "difftest-${caseInfo.caseName}"
  {
    nativeBuildInputs = [
      spike
      simulator
      dtc
      jq
    ];

    env = {
      POKEDEX_MODEL_DYLIB = "${model}/lib/libpokedex_model.so";
    };
  }
  ''
    runSpike() {
      logfile="$1"; shift
      spike --isa=rv32imafc_zvl256b_zve32x_zifencei \
        --priv=m --log-commits -p1 --hartids=0 --triggers=0 \
        -m0x80000000:0x20000000,0x40000000:0x1000 \
        --log="$logfile" \
        ${caseInfo.path}
    }

    runPokedex() {
      logfile="$1"; shift
      pokedex \
        --config-path ${./pokedex-config.kdl} \
        --output-log-path "$logfile" \
        ${caseInfo.path}
    }

    runDifftest() {
      spikelog="$1"; shift
      pokedexlog="$1"; shift
      result="$1"; shift

      difftest --spike-log-path "$spikelog" --pokedex-log-path "$pokedexlog" \
        --output-path "$result"
    }

    runSpike "spike_commit.log"
    runPokedex "pokedex_commit.jsonl"

    runDifftest "spike_commit.log" "pokedex_commit.jsonl" "result.json"

    mkdir -p $out
    cp spike_commit.log pokedex_commit.jsonl result.json "$out/"

    isSame=$(jq -r .is_same "result.json")
    if [[ "$isSame" == "true" ]]; then
      echo "[PASS] ${caseInfo.caseName}"
      exit 0
    fi

    echo "[FAIL] ${caseInfo.caseName}"
    echo "==================================================================="
    jq -r .context result.json
    exit 1
  ''
