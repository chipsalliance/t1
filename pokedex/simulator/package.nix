{
  lib,
  rustPlatform,
  rust-analyzer,
  clippy,
}:
rustPlatform.buildRustPackage (finalAttr: {
  name = "pokedex-simulator";

  src =
    with lib.fileset;
    toSource {
      root = ./.;
      fileset = unions [
        ./include
        ./assets
        ./src
        ./build.rs
        ./Cargo.lock
        ./Cargo.toml
      ];
    };

  buildInputs = [
    rustPlatform.bindgenHook
  ];

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
