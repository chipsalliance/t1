{ lib
, enableDebugging
, libspike
, libspike_interfaces
, callPackage
, elaborateConfig

, rustPlatform

, rust-analyzer
, rust-bindgen

, verilator
, verilated
, cmake
, clang-tools
}:

let
  self = rustPlatform.buildRustPackage {
    name = "difftest";
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
      libspike_interfaces
      verilated
    ];

    nativeBuildInputs = [
      verilator
      cmake
    ];

    buildFeatures = lib.optionals verilated.enable-trace [ "trace" ];

    env = {
      VERILATED_INC_DIR = "${verilated}/include";
      VERILATED_LIB_DIR = "${verilated}/lib";
      SPIKE_LIB_DIR = "${libspike}/lib";
      SPIKE_INTERFACES_LIB_DIR = "${libspike_interfaces}/lib";
      DESIGN_VLEN = elaborateConfig.parameter.vLen;
      DESIGN_DLEN = elaborateConfig.parameter.dLen;
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
      inherit libspike_interfaces;

      # enable debug info for difftest itself and libspike
      withDebug = self.overrideAttrs (old: {
        cargoBuildType = "debug";
        doCheck = false;
        env = old.env // {
          SPIKE_LIB_DIR = "${enableDebugging libspike}/lib";
        };
        dontStrip = true;
      });
    };
  };
in
self
