{ lib
, rustPlatform
, rustfmt
}:

rustPlatform.buildRustPackage {
  src = ./.;
  name = "profiler";

  cargoLock = {
    lockFile = ./Cargo.lock;
  };
}
