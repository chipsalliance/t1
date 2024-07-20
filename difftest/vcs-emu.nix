{ lib
, callPackage
, elaborateConfig

, rustPlatform

, rust-analyzer
, rust-bindgen

, vcStaticInstallPath
, vcs-lib

, cmake
, clang-tools
}:

let
  spike_interfaces = callPackage ./spike_interfaces { };

  self = rustPlatform.buildRustPackage {
    name = "t1-vcs-emu";
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
      spike_interfaces
      vcs-lib
    ];

    buildFeatures = lib.optionals vcs-lib.enable-trace [ "trace" ];
    buildAndTestSubdir = "./online_vcs";

    env = {
      VCS_LIB_DIR = "${vcStaticInstallPath}/vcs-mx/linux64/lib";
      VCS_COMPILED_LIB_DIR = "${vcs-lib}/lib";
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
      inherit spike_interfaces;
    };
  };
in
self
