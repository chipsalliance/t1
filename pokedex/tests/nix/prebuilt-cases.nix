{
  lib,
  rv32-stdenv,
  riscv-vector-tests,
  pokedex-configs,
  riscv-tests,
  meson,
  ninja,
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
rv32-stdenv.mkDerivation (finalAttrs: {
  name = "pokedex-tests-prebuilt-cases";

  nativeBuildInputs = [
    meson
    ninja
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
      ];
    };

  mesonFlags = [
    (lib.mesonOption "xlen" (toString xlen))
    (lib.mesonOption "vlen" (toString vlen))
    (lib.mesonOption "abi" abi)
    (lib.mesonOption "march" march)
    (lib.mesonEnable "zve32f" vector_has_fp)
    (lib.mesonEnable "scalar_fp" scalar_fp)
    (lib.mesonOption "riscv_tests_src" "${riscv-tests}")
    (lib.mesonOption "codegen_install_dir" "${riscv-vector-tests}")
  ];

  dontFixup = true;
})
