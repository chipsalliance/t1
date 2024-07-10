{ rustPlatform
, c-dpi-lib
, rocketv-verilated-csrc
, zlib
, rust-analyzer
, rustfmt
}:
let
  self = rustPlatform.buildRustPackage {
    name = "rocket-driver";

    src = ./.;

    cargoLock = {
      lockFile = ./Cargo.lock;
    };

    buildInputs = [ zlib ];

    env = {
      ROCKET_DPI_DIR = toString c-dpi-lib;
      TESTBENCH_LIB_DIR = toString rocketv-verilated-csrc;
    };

    passthru.devShell = self.overrideAttrs (old: {
      nativeBuildInputs = old.nativeBuildInputs ++ [
        rust-analyzer
        rustfmt
      ];
    });
  };
in
self
