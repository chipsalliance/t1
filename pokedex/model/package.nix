{
  lib,
  stdenv,
  fetchFromGitHub,
  riscv-opcodes-src,
  asl-interpreter,
  python3,
  ninja,
  minijinja,
  aslref,
  pokedex-configs,
}:
let
  softfloat-riscv = stdenv.mkDerivation {
    name = "softfloat-for-pokedex";

    src = fetchFromGitHub {
      owner = "ucb-bar";
      repo = "berkeley-softfloat-3";
      rev = "a0c6494cdc11865811dec815d5c0049fba9d82a8";
      hash = "sha256-TO1DhvUMd2iP5gvY9Hqy9Oas0Da7lD0oRVPBlfAzc90=";
    };

    makeFlags = [
      # TODO: replace this with configurable map if we want to support running pokedex simulator on AArch64
      "--directory=build/Linux-x86_64-GCC"
      "SPECIALIZE_TYPE=RISCV"
    ];

    installPhase = ''
      runHook preInstall

      mkdir -p "$out/lib"
      cp build/Linux-x86_64-GCC/softfloat.a "$out/lib/libsoftfloat.a"
      cp -r source/include "$out/"

      runHook postInstall
    '';
  };
in
stdenv.mkDerivation {
  name = "pokedex-model";
  src =
    with lib.fileset;
    toSource {
      root = ./.;
      fileset = unions [
        ./aslbuild
        ./csr
        ./csrc
        ./data_files
        ./extensions
        ./handwritten
        ./scripts
        ./template
      ];
    };

  nativeBuildInputs = [
    asl-interpreter
    python3
    ninja
    minijinja
    aslref
  ];

  env = {
    RISCV_OPCODES_SRC = "${riscv-opcodes-src}";

    SOFTFLOAT_RISCV_INCLUDE = "${softfloat-riscv}/include";

    SOFTFLOAT_RISCV_LIB = "${softfloat-riscv}/lib/libsoftfloat.a";

    # Do not let model depend on other parts of pokedex in nix build,
    # therefore directly pull the include directory.
    POKEDEX_INCLUDE = "${../simulator/include}";

    POKEDEX_CONFIG = "${pokedex-configs.src}";

  }
  // lib.optionalAttrs (!(pokedex-configs.profile.ext ? f && pokedex-configs.profile.ext ? zve32f)) {
    # Disable instruction opcode check because rv_v have both fp and non-fp instruction
    BUILDGEN_NO_CHECK = "1";
  };

  passthru = {
    inherit softfloat-riscv;
  };

  configurePhase = ''
    runHook preConfigure

    python -m scripts.buildgen

    runHook postConfigure
  '';

  # buildPhase will use ninja

  installPhase = ''
    runHook preInstall

    mkdir -p $out/include $out/lib

    cp -v -t $out/include build/2-cgen/*.h
    cp -v -t $out/lib build/3-clib/*.a
    cp -v -t $out/lib build/3-clib/*.so

    cp -v -r build/docs $out/docs

    runHook postInstall
  '';
}
