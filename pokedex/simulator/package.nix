{
  lib,
  rustPlatform,
  asl-interpreter,
  model,
  rust-analyzer,
  clippy,
  softfloat,
  softfloat-ext,
}:
rustPlatform.buildRustPackage (finalAttr: {
  name = "pokedex-simulator";

  src = lib.cleanSource ./.;

  buildInputs = [
    rustPlatform.bindgenHook
    model
  ];

  env = {
    ASL_LIB_DIR = "${asl-interpreter}/lib";
    ASL_INC_DIR = "${asl-interpreter}/include";
    POKEDEX_LIB_DIR = "${model}/lib";
    POKEDEX_INC_DIR = "${model}/include";
    SOFTFLOAT_LIB_DIR = "${softfloat}/lib";
    SOFTFLOAT_EXT_LIB_DIR = "${softfloat-ext}/lib";
  };

  passthru.shell = finalAttr.overrideAttrs (old: {
    nativeBuildInputs = old.nativeBuildInputs ++ [
      rust-analyzer
      clippy
    ];
  });

  cargoLock = {
    lockFile = ./Cargo.lock;
  };

  meta.mainProgram = "pokedex";
})
