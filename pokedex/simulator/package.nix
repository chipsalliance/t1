{
  lib,
  rustPlatform,
  asl-interpreter,
  sim-lib,
  rust-analyzer,
  clippy,
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
