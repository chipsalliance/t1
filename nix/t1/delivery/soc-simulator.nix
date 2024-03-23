{ stdenv
, fetchFromGitHub
, verilator
, subsystem-rtl
, zlib
}:

stdenv.mkDerivation {
  name = "soc-simulator";
  src = fetchFromGitHub {
    owner = "cyyself";
    repo = "soc-simulator";
    rev = "t1_dev";
    hash = "sha256-Y78VSaMpABRg6pd7ImJoUbDIIR4AtJyD9OPtNWssrR4=";
  };

  nativeBuildInputs = [
    verilator
  ];

  buildInputs = [
    zlib
    zlib.dev
  ];

  buildPhase = ''
    runHook preBuild

    ln -s ${subsystem-rtl} subsystem-rtl
    make 'INC_FILE=subsystem-rtl/*.sv' INC_DIR=subsystem-rtl/

    runHook postBuild
  '';

  installPhase = ''
    runHook preInstall

    mkdir -p $out/bin
    cp ./obj_dir/VT1Subsystem $out/bin/$name

    runHook postInstall
  '';
}
