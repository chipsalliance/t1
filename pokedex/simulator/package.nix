{
  lib,
  rustPlatform,
  asl-interpreter,
  sim-lib,
  rust-analyzer,
}:
rustPlatform.buildRustPackage (finalAttr: {
  name = "pokedex-simulator";

  src = lib.cleanSource ./.;

  buildInputs = [
    rustPlatform.bindgenHook
    sim-lib
  ];

  env = {
    ASL_LIB_DIR = "${asl-interpreter}/lib";
    ASL_INC_DIR = "${asl-interpreter}/include";
    POKEDEX_LIB_DIR = "${sim-lib}/lib";
    POKEDEX_INC_DIR = "${sim-lib}/include";
  };

  passthru.dev = finalAttr.overrideAttrs (old: {
    nativeBuildInputs = old.nativeBuildInputs ++ [
      rust-analyzer
    ];
  });

  cargoLock = {
    lockFile = ./Cargo.lock;
  };

  meta.mainProgram = "pokedex";
})
