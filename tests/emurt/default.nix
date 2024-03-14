{ stdenv, bintools }:

stdenv.mkDerivation {
  name = "emurt";
  nativeBuildInputs = [ bintools ];

  NIX_CFLAGS_COMPILE = "-mabi=ilp32f -march=rv32gcv -fno-PIC";

  buildCommand = ''
    mkdir -p $out/lib
    ${stdenv.targetPlatform.config}-cc ${./emurt.c} -c -o emurt.o
    ${stdenv.targetPlatform.config}-ar rcs $out/lib/libemurt.a emurt.o
  '';
}
