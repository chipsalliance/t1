{ lib
, rustPlatform
, libspike
, spike_interfaces
, enable-trace ? false
, vcStaticHome
}:

rustPlatform.buildRustPackage {
  name = "vcs-dpi-lib";
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

  buildFeatures = lib.optionals enable-trace [ "trace" ];
  buildAndTestSubdir = "./online_vcs";

  env = {
    VCS_LIB_DIR = "${vcStaticHome}/vcs-mx/linux64/lib";
    SPIKE_LIB_DIR = "${libspike}/lib";
    SPIKE_INTERFACES_LIB_DIR = "${spike_interfaces}/lib";
  };

  cargoLock = {
    lockFile = ../Cargo.lock;
  };

  passthru = {
    inherit enable-trace;
  };
}
