{ stdenv, cmake, libspike }:
let
  pname = "libspike_interfaces";
  version = "0.1.0";
  cmakeConfig = ''
    add_library(libspike_interfaces STATIC IMPORTED GLOBAL)
    set_target_properties(libspike_interfaces PROPERTIES
      IMPORTED_LOCATION "${placeholder "out"}/lib/libspike_interfaces.so")
    target_include_directories(libspike_interfaces AFTER INTERFACE
      "${placeholder "out"}/include"
    )
  '';
in
stdenv.mkDerivation rec {
  inherit pname version cmakeConfig;
  src = ../../difftest/libspike_interfaces;
  nativeBuildInputs = [ cmake ];
  propagatedBuildInputs = [ libspike ];
  postInstall = ''
    mkdir -p $out/include $out/lib/cmake/libspike_interfaces
    cp $src/spike_interfaces.h $out/include
    cp $src/spike_interfaces_c.h $out/include
    echo "$cmakeConfig" > $out/lib/cmake/libspike_interfaces/libspike_interfaces-config.cmake
  '';
}