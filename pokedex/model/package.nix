{
  lib,
  stdenv,
  rvopcode-cli,
  riscv-opcodes-src,
  asl-interpreter,
  python3,
  ninja,
  minijinja,
}:
stdenv.mkDerivation {
  name = "pokedex-model";
  src = lib.cleanSource ./.;

  nativeBuildInputs = [
    rvopcode-cli
    asl-interpreter
    python3
    ninja
    minijinja
  ];

  env = {
    RISCV_OPCODES_SRC = "${riscv-opcodes-src}";
  };

  configurePhase = ''
    python -m scripts.buildgen
  '';

  # buildPhase will use ninja

  installPhase = ''
    mkdir -p $out/include
    mkdir -p $out/lib
    cp -v -t $out/include build/2-cgen/*.h
    cp -v -t $out/lib build/3-clib/*.a
  '';
}
