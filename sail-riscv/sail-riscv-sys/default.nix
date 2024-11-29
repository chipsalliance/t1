{ lib
, rustPlatform
, sail-riscv-c-model
, rustfmt
, rust-analyzer
, gmp
}:

let
  finalPkg = rustPlatform.buildRustPackage {
    name = "sail-riscv-rs-emulator";
    src = with lib.fileset; toSource {
      root = ./.;
      fileset = unions [
        ./src
        ./build.rs
        ./sail_include
        ./Cargo.lock
        ./Cargo.toml
      ];
    };

    nativeBuildInputs = [
      rustPlatform.bindgenHook
    ];

    buildInputs = [ gmp ];

    env = {
      SAIL_INSTALL_PATH = toString sail-riscv-c-model.sail;
    };

    cargoLock = {
      lockFile = ./Cargo.lock;
    };

    passthru = {
      devShell = finalPkg.overrideAttrs (prev: {
        nativeBuildInputs = prev.nativeBuildInputs ++ [
          rustfmt
          rust-analyzer
        ];
      });
    };
  };
in
finalPkg
