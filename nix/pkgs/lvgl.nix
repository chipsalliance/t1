{
  lib,
  stdenv,
  fetchFromGitHub,
  cmake,
  ninja,
}:

let
  preset = "linux-base";
in

stdenv.mkDerivation rec {
  pname = "lvgl";
  version = "9.2.1";

  src = fetchFromGitHub {
    owner = "lvgl";
    repo = "lvgl";
    rev = "v${version}";
    hash = "sha256-+k2ID3nzwrxuQC/1lR/RrEUNoyHfnjVQd3NpyqakD3g=";
  };

  nativeBuildInputs = [
    cmake
    ninja
  ];

  ninjaFlags = [ "-C" preset ];

  cmakeFlagsArray = [ "--preset" preset ];

  meta = {
    description = "Embedded graphics library to create beautiful UIs for any MCU, MPU and display type";
    homepage = "https://github.com/lvgl/lvgl";
    license = lib.licenses.mit;
    platforms = lib.platforms.all;
  };
}
