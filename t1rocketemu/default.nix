{ lib
, newScope
, rustPlatform
, zlib
, libspike
, libspike_interfaces
, cmake
, verilator
}:
lib.makeScope newScope (scope: rec {
  mlirbc = scope.callPackage ./nix/mlirbc.nix { };
  rtl = scope.callPackage ./nix/rtl.nix { };
  verilated-c-lib = scope.callPackage ./nix/verilated-c-lib.nix { };

  emu = rustPlatform.buildRustPackage {
    name = "t1rocketemu";

    src = with lib.fileset; toSource {
      root = ./.;
      fileset = unions [
        ./test_common
        ./spike_rs
        ./offline
        ./online_dpi
        ./online_drive
        ./online_vcs
        ./Cargo.lock
        ./Cargo.toml
      ];
    };

    buildInputs = [
      zlib
      libspike_interfaces
      verilated-c-lib
    ];

    nativeBuildInputs = [
      verilator
      cmake
    ];

    # FIXME: can we hack this into derivations, so that we don't need to specify library dir explicitly?
    env =
      let
        toLib = drv: "${drv}/lib";
      in
      {
        SPIKE_LIB_DIR = toLib libspike;
        SPIKE_INTERFACES_LIB_DIR = toLib libspike_interfaces;
        VERILATED_INC_DIR = "${verilated-c-lib}/include";
        VERILATED_LIB_DIR = "${verilated-c-lib}/lib";
      };

    cargoLock = {
      lockFile = ./Cargo.lock;
    };

    outputs = [ "out" "driver" "offline" ];

    postInstall = ''
      mkdir -p $driver/bin $offline/bin
      ln -s $out/bin/driver $driver/bin/driver
      ln -s $out/bin/offline $driver/bin/offline
    '';
  };
})
