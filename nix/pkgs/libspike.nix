{ stdenv, dtc, fetchFromGitHub }:

stdenv.mkDerivation {
  version = "unstable-2024-02-20";
  pname = "libspike";

  env.cmakeConfig = ''
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
  nativeBuildInputs = [ dtc ];
  enableParallelBuilding = true;
  src = fetchFromGitHub {
    owner = "riscv";
    repo = "riscv-isa-sim";
    rev = "a22119e562c4185275a10eb11ca4ba568d09a570";
    sha256 = "sha256-FR+so/kFbdSdlAQ6/P4WlD+wr18R4LXCwaNSTlki8mk=";
  };
  configureFlags = [
    "--enable-commitlog"
  ];

  patches = [ ../patches/spike/order_of_unordered_reduce.patch ];

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
