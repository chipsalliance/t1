{ lib
, libspike
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
  spike_interfaces = callPackage ./spike_interfaces { };

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

  env = {
    VERILATED_INC_DIR = "${verilated}/include";
    VERILATED_LIB_DIR = "${verilated}/lib";
    SPIKE_LIB_DIR = "${libspike}/lib";
    SPIKE_INTERFACES_LIB_DIR = "${spike_interfaces}/lib";
    SPIKE_ISA_STRING =
      "rv32gc" +
      (builtins.concatStringsSep "_" elaborateConfig.parameter.extensions)
      + "_Zvl${toString elaborateConfig.parameter.vLen}b";
    DESIGN_VLEN = elaborateConfig.parameter.vLen;
    DESIGN_DLEN = elaborateConfig.parameter.dLen;
  };

  online-dpi-lib = rustPlatform.buildRustPackage {
    name = "online-dpi-lib";

    inherit src env;

    cargoLock = {
      lockFile = ./Cargo.lock;
    };

    buildFeatures = lib.optionals verilated.enable-trace [ "trace" ];
    buildAndTestSubdir = "./online_vcs";
  };

  self = rustPlatform.buildRustPackage {
    name = "verilator-emu" + (lib.optionalString verilated.enable-trace "-trace");
    inherit src env;

    buildInputs = [
      spike_interfaces
      verilated
    ];

    nativeBuildInputs = [
      verilator
      cmake
    ];

    buildFeatures = lib.optionals verilated.enable-trace [ "trace" ];
    buildAndTestSubdir = "./online_drive";

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
      inherit spike_interfaces online-dpi-lib;
    };
  };
in
self
