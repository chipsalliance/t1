{ lib
, callPackage

, rustPlatform

, rust-analyzer
, rust-bindgen

, libspike
, verilator
, verilated
, cmake
}:

let
  libspike_interfaces = callPackage ./libspike_interfaces { };
  # verilated = callPackage ./verilated { };

  self = rustPlatform.buildRustPackage {
    name = "t1-simulator";
    src = with lib.fileset; toSource {
      root = ./.;
      fileset = unions [
        ./libspike_rs
        ./offline
        ./online_drive
        ./Cargo.lock
        ./Cargo.toml
      ];
    };

    buildInputs = [
      libspike
      libspike_interfaces
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
        ];
      });
      inherit libspike_interfaces;
    };
  };
in
self
