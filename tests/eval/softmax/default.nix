{
  lib,
  linkerScript,
  makeBuilder,
  t1main,
  python3,
}:
let
  builder = makeBuilder { casePrefix = "eval"; };
  python3WithTorch = python3.withPackages (ps: [
    ps.torch
    ps.numpy
  ]);
in
builder {
  caseName = "softmax";
  src =
    (
      with lib.fileset;
      toSource {
        root = ./.;
        fileset = unions [
          ./softmax.c
          ./safe_softmax.py
        ];
      }
    ).outPath;

  nativeBuildInputs = [ python3WithTorch ];

  passthru.featuresRequired = { };
  isFp = true;

  # env.DO_DIFF_TEST = 1;
  # env.FAST_EXP = 1;

  buildPhase = ''
    runHook preBuild

    python3 ./safe_softmax.py dump

    if [[ -n "$DO_DIFF_TEST" ]]; then
      NIX_CFLAGS_COMPILE="-DDO_DIFF_TEST $NIX_CFLAGS_COMPILE"
    fi

    if [[ -n "$FAST_EXP" ]]; then
      NIX_CFLAGS_COMPILE="-DFAST_EXP $NIX_CFLAGS_COMPILE"
    fi

    $CC -T${linkerScript} \
      softmax.c \
      ${t1main} \
      -o $pname.elf

    runHook postBuild
  '';
}
