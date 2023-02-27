{ stdenv, dtc, fetchFromGitHub }:
let
  version = "1.1.0";
  pname = "libspike";
  cmakeConfig = ''
    add_library(libspike STATIC IMPORTED GLOBAL)
    set_target_properties(libspike PROPERTIES
      IMPORTED_LOCATION "${placeholder "out"}/lib/libriscv.so")
    target_include_directories(libspike INTERFACE
      "${placeholder "out"}/include"
      "${placeholder "out"}/include/riscv"
      "${placeholder "out"}/include/fesvr"
      "${placeholder "out"}/include/softfloat"
    )
  '';
in
stdenv.mkDerivation {
  inherit version pname cmakeConfig;
  nativeBuildInputs = [ dtc ];
  enableParallelBuilding = true;
  src = fetchFromGitHub {
    owner = "riscv";
    repo = "riscv-isa-sim";
    rev = "ab3225a3ff687fda8b4180f9e4e0949a400d1247";
    sha256 = "sha256-2cC2goTmxWnkTm3Tq08R8YkkuI2Fj8fRvpEPVZ5JvUI=";
  };
  configureFlags = [
    "--enable-commitlog"
  ];
  installPhase = ''
    runHook preInstall
    mkdir -p $out/include/{riscv,fesvr,softfloat} $out/lib $out/lib/cmake/libspike
    cp riscv/*.h $out/include/riscv
    cp fesvr/*.h $out/include/fesvr
    cp softfloat/*.h $out/include/softfloat
    cp config.h $out/include
    cp *.so $out/lib
    echo "$cmakeConfig" > $out/lib/cmake/libspike/libspike-config.cmake
    runHook postInstall
  '';
}
