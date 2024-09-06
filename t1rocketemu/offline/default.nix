{ lib
, rustPlatform
, libspike
, libspike_interfaces
, rtlDesignMetadata
}:


rustPlatform.buildRustPackage {
  name = "vcs-offline-checker";
  buildAndTestSubdir = "./offline";

  src = with lib.fileset; toSource {
    root = ../.;
    fileset = unions [
      ../spike_rs
      ../offline
      ../test_common
      ../Cargo.lock
      ../Cargo.toml
    ];
  };

  env = {
    SPIKE_LIB_DIR = "${libspike}/lib";
    SPIKE_INTERFACES_LIB_DIR = "${libspike_interfaces}/lib";
    DESIGN_VLEN = rtlDesignMetadata.vlen;
    DESIGN_DLEN = rtlDesignMetadata.dlen;
    SPIKE_ISA_STRING = rtlDesignMetadata.march;
  };

  cargoLock = {
    lockFile = ../Cargo.lock;
  };
}
