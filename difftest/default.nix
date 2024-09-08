{ lib
, rustPlatform
, libspike
, libspike_interfaces
, rtlDesignMetadata
}:

{ outputName
, emuType
, buildType
, enableTrace ? false
}:

assert lib.assertMsg (lib.elem emuType [ "verilator" "vcs" ]) "emuType is either 'vcs' nor 'verilator'";
assert lib.assertMsg (lib.elem buildType [ "t1" "t1rocket" ]) "emuType is either 't1' nor 't1rocket'";

rustPlatform.buildRustPackage {
  name = outputName;
  src = with lib.fileset; toSource {
    root = ./.;
    fileset = unions [
      ./spike_rs
      ./offline_t1
      ./offline_t1rocket
      ./dpi_common
      ./dpi_t1
      ./dpi_t1rocket
      ./test_common
      ./Cargo.lock
      ./Cargo.toml
    ];
  };

  buildFeatures = [ "dpi_common/${emuType}" ] ++ lib.optionals enableTrace [ "dpi_common/trace" ];
  buildAndTestSubdir = "./dpi_${buildType}";

  env = {
    SPIKE_LIB_DIR = "${libspike}/lib";
    SPIKE_INTERFACES_LIB_DIR = "${libspike_interfaces}/lib";
    DESIGN_VLEN = rtlDesignMetadata.vlen;
    DESIGN_DLEN = rtlDesignMetadata.dlen;
    SPIKE_ISA_STRING = rtlDesignMetadata.march;
  };

  cargoLock = {
    lockFile = ./Cargo.lock;
  };

  passthru = {
    dpiLibPath = "/lib/libdpi_${buildType}.a";
    inherit enableTrace;
  };
}
