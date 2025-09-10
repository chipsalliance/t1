{
  lib,
  stdenv,
  softfloat,
}:
stdenv.mkDerivation {
  name = "softfloat_ext";
  src =
    with lib.fileset;
    toSource {
      root = ./.;
      fileset = unions [
        ./wrapper.c
        ./Makefile
      ];
    };

  propagatedBuildInputs = [
    softfloat
  ];

  makeFlags = [
    "PREFIX=${placeholder "out"}"
  ];
}
