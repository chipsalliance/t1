{ lib
, libspike
, rustPlatform
, libspike_interfaces
, rtl
}:

rustPlatform.buildRustPackage {
  name = "offline_difftest";
  src = with lib.fileset; toSource {
    root = ./.;
    fileset = fileFilter (file: file.name != "default.nix") ./.;
  };
  buildInputs = [ libspike libspike_interfaces ];
  cargoLock = {
    lockFile = ./Cargo.lock;
  };
}
