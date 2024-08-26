{ lib
, rustPlatform
, libspike
, libspike_interfaces
, rtlDesignMetadata
, vcStaticHome
}:

let
  build = { name, buildAndTestSubdir, enable-trace ? false }:
    rustPlatform.buildRustPackage
      {
        inherit name buildAndTestSubdir;

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

        env = {
          VCS_LIB_DIR = "${vcStaticHome}/vcs-mx/linux64/lib";
          SPIKE_LIB_DIR = "${libspike}/lib";
          SPIKE_INTERFACES_LIB_DIR = "${libspike_interfaces}/lib";
          DESIGN_VLEN = rtlDesignMetadata.vlen;
          DESIGN_DLEN = rtlDesignMetadata.dlen;
          SPIKE_ISA_STRING = rtlDesignMetadata.march;
        };

        cargoLock = {
          lockFile = ../Cargo.lock;
        };

        passthru = {
          inherit enable-trace;
        };
      };
in
{
  vcs-dpi-lib = build { name = "vcs-dpi-lib"; buildAndTestSubdir = "./online_vcs"; };
  vcs-dpi-lib-trace = build { name = "vcs-dpi-lib"; buildAndTestSubdir = "./online_vcs"; enable-trace = true; };
  vcs-offline-checker = build { name = "vcs-offline-checker"; buildAndTestSubdir = "./offline"; };
}
