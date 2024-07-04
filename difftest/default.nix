{ lib
, callPackage

, rustPlatform

, rust-analyzer
, rust-bindgen

, verilator
, verilated
, cmake
, clang-tools
}:

let
  spike_interfaces = callPackage ./spike_interfaces { };

  self = rustPlatform.buildRustPackage {
    name = "t1-simulator";
    src = with lib.fileset; toSource {
      root = ./.;
      fileset = unions [
        ./spike_rs
        ./offline
        ./online_drive
        ./test_common
        ./Cargo.lock
        ./Cargo.toml
      ];
    };

    buildInputs = [
      spike_interfaces
      verilated
    ];

    nativeBuildInputs = [
      verilator
      cmake
    ];

    env = {
      VERILATED_INC_DIR = "${verilated}/include";
      VERILATED_LIB_DIR = "${verilated}/lib";
    };

    cargoLock = {
      lockFile = ./Cargo.lock;
    };

    dontUseCmakeConfigure = true;

    passthru = {
      devShell = self.overrideAttrs (old: {
        nativeBuildInputs = old.nativeBuildInputs ++ [
          rust-analyzer
          rust-bindgen
          clang-tools
        ];
      });
      inherit spike_interfaces;
    };
  };
in
self
