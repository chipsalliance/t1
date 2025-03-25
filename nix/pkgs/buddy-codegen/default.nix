{ lib
, stdenv
, argparse
, buddy-mlir
, libpng
}:
stdenv.mkDerivation {
  name = "buddy-codegen";

  src = with lib.fileset; toSource {
    fileset = unions [
      ./dip.mlir
      ./buddy-codegen.cc
    ];
    root = ./.;
  };

  buildInputs = [ libpng argparse buddy-mlir ];

  env.NIX_CFLAGS_COMPILE = toString [
    # TODO: BMP is now broken
    "-lpng"
    "-DBUDDY_ENABLE_PNG"
    "-O3"
  ];

  buildPhase = ''
    runHook preBuild
    # We don't need to care about stripmining size here
    buddy-opt dip.mlir \
      -lower-dip="DIP-strip-mining=256" \
      -arith-expand \
      -lower-affine \
      -llvm-request-c-wrappers \
      -convert-scf-to-cf \
      -convert-math-to-llvm \
      -convert-vector-to-llvm \
      -finalize-memref-to-llvm \
      -convert-func-to-llvm \
      -reconcile-unrealized-casts | \
    buddy-translate --mlir-to-llvmir | \
    buddy-llc \
        --filetype=obj \
        -o dip.o

    $CXX ./dip.o ./buddy-codegen.cc -o buddy-codegen
    runHook postBuild
  '';

  installPhase = ''
    runHook preInstall
    mkdir -p $out/bin
    cp -v buddy-codegen $out/bin/
    runHook postInstall
  '';
}
