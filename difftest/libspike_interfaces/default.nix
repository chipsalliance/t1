{ lib, stdenv, cmake, libspike }:

stdenv.mkDerivation {
  name = "libspike_interfaces";
  src = with lib.fileset; toSource {
    root = ./.;
    fileset = fileFilter (file: file.name != "default.nix") ./.;
  };
  nativeBuildInputs = [ cmake ];
  propagatedBuildInputs = [ libspike ];
}
