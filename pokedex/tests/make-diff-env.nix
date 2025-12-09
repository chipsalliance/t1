# To debug, developer can run "nix develop .#<attr>" and run following command to replay difftest:
#
# ```bash
# $ runPhase configurePhase
# $ runPhase checkPhase
# ```
#
# This builder already save all the test log even on difftest failure.
# But for debug usage only, developer can overwrite the derivation with any hook to set env "ignoreFailure"
# and let nix build never fail.
#
# ```nix
# test.overrideAttrs {
#   ignoreFailure = true;
# };
# ```

{
  stdenvNoCC,
  spike,
  dtc,
  model,
  simulator,
  jq,
  pokedex-configs,
}:

{
  caseName,
  casePath,
  caseDump,
}:
stdenvNoCC.mkDerivation {
  name = "pokedex-tests-diffenv_${caseName}";

  nativeBuildInputs = [
    spike
    dtc
    simulator
    jq
  ];

  env = {
    POKEDEX_MODEL_DYLIB = "${model}/lib/libpokedex_model.so";

    # Basically for intenal usage, but could help nix develop use case
    spikeLog = "spike_commit.log";
    pokedexLog = "pokedex_commit.jsonl";
    diffResult = "diff_result.json";
    pokedex = "pokedex";
    spike = "spike";

    inherit caseName casePath caseDump;
  };

  configurePhase = ''
    runHook preConfigure

    workdir="$(mktemp -d -t "$name.XXX")"
    cd "$workdir"

    if [[ -f "$caseDump" ]]; then
      cp "$caseDump" "$workdir"
    fi

    runHook postConfigure
  '';

  spikePhase = ''
    runHook preRunSpike

    "$spike" --isa=${pokedex-configs.profile.march} \
      --priv=m --log-commits -p1 --hartids=0 --triggers=0 \
      -m0x80000000:0x20000000,0x40000000:0x1000 \
      --log="$spikeLog" \
      "$casePath"

    runHook postRunSpike
  '';

  pokedexPhase = ''
    runHook preRunPokedex

    "$pokedex" run \
      --config-path ${./pokedex-config.kdl} \
      --output-log-path "$pokedexLog" \
      "$casePath"

    runHook postRunPokedex
  '';

  diffPhase = ''
    runHook preDiff

    "$pokedex" difftest \
      --spike-log-path "$spikeLog" \
      --pokedex-log-path "$pokedexLog" \
      --output-path "$diffResult"

    runHook postDiff
  '';

  doCheck = true;
  checkPhase = ''
    runHook preCheck

    runPhase spikePhase
    runPhase pokedexPhase
    runPhase diffPhase

    runHook postCheck
  '';

  installPhase = ''
    runHook preInstall

    mkdir -p "$out"
    mv ./* "$out"
    cd "$out"

    isSame=$(jq -r .is_same "$diffResult")
    if [[ "$isSame" == "true" ]]; then
      echo "[PASS] $caseName"
      exit 0
    fi

    echo "[FAIL] $caseName"
    echo "NOTE: see $diffResult in "nix develop" for details"

    if [[ -z "''${ignoreFailure:-}" ]]; then
      exit 1
    fi

    runHook postInstall
  '';

  # don't touch
  phases = "configurePhase checkPhase installPhase";
}
