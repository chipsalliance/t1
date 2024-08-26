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
, verilator-emu-rtl-verilated
, cmake
, clang-tools

, rtlDesignMetadata
}:

let
  self = rustPlatform.buildRustPackage {
    name = "verilator-emu" + (lib.optionalString verilator-emu-rtl-verilated.enable-trace "-trace");

    src = with lib.fileset; toSource {
      root = ./.;
      fileset = unions [
        ./spike_rs
        ./offline
        ./online_dpi
        ./online_drive
        ./online_vcs
        ./test_common
        ./Cargo.lock
        ./Cargo.toml
      ];
    };

    buildInputs = [
      libspike_interfaces
      verilator-emu-rtl-verilated
    ];

    nativeBuildInputs = [
      verilator
      cmake
    ];

    buildFeatures = lib.optionals verilator-emu-rtl-verilated.enable-trace [ "trace" ];

    env = {
      VERILATED_INC_DIR = "${verilator-emu-rtl-verilated}/include";
      VERILATED_LIB_DIR = "${verilator-emu-rtl-verilated}/lib";
      SPIKE_LIB_DIR = "${libspike}/lib";
      SPIKE_INTERFACES_LIB_DIR = "${libspike_interfaces}/lib";
      SPIKE_ISA_STRING =
        "rv32gc" +
        (builtins.concatStringsSep "_" elaborateConfig.parameter.extensions)
        + "_Zvl${toString elaborateConfig.parameter.vLen}b";
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

      inherit libspike_interfaces rtlDesignMetadata;
      inherit (verilator-emu-rtl-verilated) enable-trace;

      # enable debug info for difftest itself and libspike
      withDebug = self.overrideAttrs (old: {
        cargoBuildType = "debug";
        doCheck = false;
        env = old.env // {
          SPIKE_LIB_DIR = "${enableDebugging libspike}/lib";
        };
        dontStrip = true;
      });

      # Here is an curry function:
      #
      #   * run-emulator.nix return type
      #   run-emulator :: { callPackage Args... } -> emulatorDerivation -> testCase -> runCommandDerivation
      #
      #   * runEmulation attribute type:
      #   runEmulation :: testCase -> runCommandDerivation
      #
      runEmulation = (callPackage ./run-emulator.nix { }) self;

      cases = callPackage ../tests { emulator = self; };
    };
  };
in
self
