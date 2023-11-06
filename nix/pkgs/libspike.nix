{ stdenv, dtc, fetchFromGitHub }:
let
  version = "1.1.0";
  pname = "libspike";
  cmakeConfig = ''
    add_library(libspike STATIC IMPORTED GLOBAL)
    set_target_properties(libspike PROPERTIES
      IMPORTED_LOCATION "${placeholder "out"}/lib/libriscv.so")
    target_include_directories(libspike AFTER INTERFACE
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
    rev = "eb75ab37a17ff4f8597b7b40283a08c38d2a6ff6";
    sha256 = "sha256-slr2sMXNhNAIJYMpe9A5rVrYUuhNIYE8G1Y9tR2qP8s=";
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
