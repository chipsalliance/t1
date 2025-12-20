{
  lib,
  stdenvNoCC,
  pokedex-configs,
  meson,
  rsync,
  ninja,
  spike,
  dtc,
  model,
  simulator,
  python3,
  prebuilt-cases,
}:
let
  inherit (pokedex-configs.profile)
    march
    vlen
    xlen
    abi
    ;

  vector_has_fp = (pokedex-configs.profile.ext ? zve32f) && pokedex-configs.profile.ext.zve32f;
  scalar_fp = (pokedex-configs.profile.ext ? f) && pokedex-configs.profile.ext.f;
in
stdenvNoCC.mkDerivation (finalAttrs: {
  name = "pokedex-tests-result";

  nativeBuildInputs = [
    meson
    ninja
    spike
    dtc
    simulator
    rsync
    python3
  ];

  src =
    with lib.fileset;
    toSource {
      root = ../.;
      fileset = unions [
        ../compile-stubs
        ../riscv-tests
        ../riscv-vector-tests
        ../smoke
        ../smoke_v
        ../meson.build
        ../meson.options
        ../difftest.py
        ../pokedex-config.kdl
      ];
    };

  doCheck = true;

  passthru.env = finalAttrs.env;

  env = {
    POKEDEX_MODEL_DYLIB = "${model}/lib/libpokedex_model.so";
  };

  patchPhase = ''
    runHook prePatch

    patchShebangs --build difftest.py

    runHook postPatch
  '';

  mesonFlags = [
    (lib.mesonOption "xlen" (toString xlen))
    (lib.mesonOption "vlen" (toString vlen))
    (lib.mesonOption "abi" abi)
    (lib.mesonOption "march" march)
    (lib.mesonEnable "zve32f" vector_has_fp)
    (lib.mesonEnable "scalar_fp" scalar_fp)
    (lib.mesonEnable "with_tests" true)
    (lib.mesonOption "prebuilt_case_dir" (toString prebuilt-cases))
  ];

  dontFixup = true;
})
