{ gcc13Stdenv, dtc, fetchFromGitHub }:

gcc13Stdenv.mkDerivation {
  version = "unstable-2024-07-03";
  pname = "libspike";

  env.cmakeConfig = ''
    add_library(libspike STATIC IMPORTED GLOBAL)
    set_target_properties(libspike PROPERTIES
      IMPORTED_LOCATION "${placeholder "out"}/lib/libriscv.a")
    target_include_directories(libspike AFTER INTERFACE
      "${placeholder "out"}/include"
      "${placeholder "out"}/include/riscv"
      "${placeholder "out"}/include/fesvr"
      "${placeholder "out"}/include/softfloat"
    )
  '';
  nativeBuildInputs = [ dtc ];
  enableParallelBuilding = true;
  separateDebugInfo = true;
  src = fetchFromGitHub {
    owner = "riscv";
    repo = "riscv-isa-sim";
    rev = "4a2da916671d49d9ab82f702f50995c19110c2a3";
    hash = "sha256-c+yYuz2Z2/MwGmHYcv/gPIJQluzBjw8uUlOXsf9Bz28=";
  };
  configureFlags = [
    "--enable-commitlog"
  ];

  patches = [
    ../patches/spike/0001-enforce-lanewise-order-for-unordered-reduce.patch
    ../patches/spike/0002-disable-NaN-normalization.patch
    ../patches/spike/0003-relax-vsew-vlmul-ELEN-check.patch
  ];

  installPhase = ''
    runHook preInstall
    mkdir -p $out/include/{riscv,fesvr,softfloat} $out/lib $out/lib/cmake/libspike
    cp riscv/*.h $out/include/riscv
    cp fesvr/*.h $out/include/fesvr
    cp softfloat/*.h $out/include/softfloat
    cp config.h $out/include
    cp *.so $out/lib
    cp *.a $out/lib
    echo "$cmakeConfig" > $out/lib/cmake/libspike/libspike-config.cmake
    runHook postInstall
  '';
}
