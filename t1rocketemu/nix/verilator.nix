{ lib
, enableDebugging
, callPackage
, rustPlatform
, cmake
, clang-tools

, rust-analyzer
, rust-bindgen

, libspike
, libspike_interfaces
, verilator
, verilated-c-lib

, rtlDesignMetadata
}:

let
  self = rustPlatform.buildRustPackage {
    name = "t1rocket-verilator-emu" + (lib.optionalString verilated-c-lib.enable-trace "-trace");

    src = with lib.fileset; toSource {
      root = ../.;
      fileset = unions [
        ../spike_rs
        ../offline
        ../online_dpi
        ../online_drive
        ../online_vcs
        ../test_common
        ../Cargo.lock
        ../Cargo.toml
      ];
    };

    buildInputs = [
      libspike_interfaces
      verilated-c-lib
    ];

    nativeBuildInputs = [
      verilator
      cmake
    ];

    buildFeatures = lib.optionals verilated-c-lib.enable-trace [ "trace" ];

    env = {
      VERILATED_INC_DIR = "${verilated-c-lib}/include";
      VERILATED_LIB_DIR = "${verilated-c-lib}/lib";
      SPIKE_LIB_DIR = "${libspike}/lib";
      SPIKE_INTERFACES_LIB_DIR = "${libspike_interfaces}/lib";
      SPIKE_ISA_STRING = rtlDesignMetadata.march;
      DESIGN_VLEN = rtlDesignMetadata.vlen;
      DESIGN_DLEN = rtlDesignMetadata.dlen;
    };

    cargoLock = {
      lockFile = ../Cargo.lock;
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

      inherit (verilated-c-lib) enable-trace;
      inherit libspike_interfaces rtlDesignMetadata;

      # enable debug info for difftest itself and libspike
      withDebug = self.overrideAttrs (old: {
        cargoBuildType = "debug";
        doCheck = false;
        env = old.env // {
          SPIKE_LIB_DIR = "${enableDebugging libspike}/lib";
        };
        dontStrip = true;
      });

      cases = callPackage ../../tests {
        configName = "t1rocket";
        emulator = self;
      };

      runEmulation = (callPackage ./run-emulator.nix { }) self;
    };
  };
in
self
