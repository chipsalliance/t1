{ lib
, rustPlatform
, libspike
, libspike_interfaces
}:

{ outputName
, emuType ? ""
, moduleType
}:

assert let
  available = [ "dpi_t1emu" "dpi_t1rocketemu" "t1-sim-checker" "t1emu" ];
in
lib.assertMsg (lib.elem moduleType available) "moduleType is not in ${lib.concatStringsSep ", " available}";

let
  rustSrc = with lib.fileset; toSource {
    root = ./.;
    fileset = unions [
      ./spike_rs
      ./dpi_common
      ./dpi_t1emu
      ./dpi_t1rocketemu
      ./t1-sim-checker
      ./t1devices
      ./t1emu
      ./Cargo.lock
      ./Cargo.toml
      ./.rustfmt.toml
    ];
  };
in
if (lib.hasPrefix "dpi" moduleType) then
  assert lib.assertMsg (lib.elem emuType [ "verilator" "vcs" ]) "emuType must be 'vcs' or 'verilator' for dpi";
  rustPlatform.buildRustPackage {
    name = outputName;
    src = rustSrc;

    buildFeatures = [ ] ++ [ "dpi_common/${emuType}" ];
    buildAndTestSubdir = "./${moduleType}";

    env = {
      SPIKE_LIB_DIR = "${libspike}/lib";
      SPIKE_INTERFACES_LIB_DIR = "${libspike_interfaces}/lib";
    };

    cargoLock = {
      lockFile = ./Cargo.lock;
    };

    passthru = {
      # include "lib" prefix, without ".so" suffix, for "-sv_lib" option
      svLibName = "lib${moduleType}";

      dpiLibPath = "/lib/libdpi_${moduleType}.a";
    };
  }
else
  assert lib.assertMsg (emuType == "") "emuType shall not be set for offline";
  rustPlatform.buildRustPackage {
    name = outputName;
    src = rustSrc;

    buildFeatures = [ ];
    buildAndTestSubdir = "./${moduleType}";

    env = {
      SPIKE_LIB_DIR = "${libspike}/lib";
      SPIKE_INTERFACES_LIB_DIR = "${libspike_interfaces}/lib";
    };

    cargoLock = {
      lockFile = ./Cargo.lock;
    };

    meta.mainProgram = outputName;
  }
