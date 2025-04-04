{
  lib,
  stdenv,
  bintools,
}:

stdenv.mkDerivation {
  name = "emurt";
  nativeBuildInputs = [ bintools ];

  NIX_CFLAGS_COMPILE = "-mabi=ilp32f -march=rv32gcv -fno-PIC";

  src =
    with lib.fileset;
    toSource {
      root = ./.;
      fileset = fileFilter (file: file.name != "default.nix") ./.;
    };

  buildPhase = ''
    runHook preBuild
    ${stdenv.targetPlatform.config}-cc emurt.c -c -o emurt.o
    runHook postBuild
  '';

  installPhase = ''
    runHook preInstall
    mkdir -p $out/{lib,include}

    cp *.h $out/include/
    ${stdenv.targetPlatform.config}-ar rcs $out/lib/libemurt.a emurt.o

    runHook postInstall
  '';
}
