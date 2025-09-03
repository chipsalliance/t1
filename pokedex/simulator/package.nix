{
  lib,
  rustPlatform,
  asl-interpreter,
  model,
  rust-analyzer,
  clippy,
  python3,
  softfloat,
  softfloat-ext,
}:
rustPlatform.buildRustPackage (finalAttr: {
  name = "pokedex-simulator";

  src = lib.cleanSource ./.;

  buildInputs = [
    rustPlatform.bindgenHook
    model
    python3
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

  postInstall = ''
    mv ./batchrun/batchrun.py $out/bin/batchrun
  '';

  meta.mainProgram = "pokedex";
})
