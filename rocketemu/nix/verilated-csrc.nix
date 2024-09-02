{ lib
, fetchgit
, stdenv
, rtl
, verilator
, enableTrace ? true
, zlib
}:

let
  rocket-chip-v-src = fetchgit {
    url = "https://github.com/chipsalliance/rocket-chip.git";
    rev = "833385404d9c722bdfad3e453c19a3ac6f40dbf0";
    fetchSubmodules = false;
    sparseCheckout = [
      "src/main/resources/vsrc"
    ];
    hash = "sha256-CUq9VDwb7ZtclosgOWfDZMOpH+U/yBjL5CNiXZRiB80=";
  };
in
stdenv.mkDerivation {
  name = "t1-rocketv-verilated";

  src = rtl;

  nativeBuildInputs = [ verilator ];

  propagatedBuildInputs = lib.optionals enableTrace [ zlib ];

  env.rocketChipVSrc = "${rocket-chip-v-src}/src/main/resources/vsrc/";

  buildPhase = ''
    runHook preBuild

    echo "[nix] running verilator"
    # FIXME: fix all the warning and remove -Wno-<msg> flag here
    verilator \
      -I"$rocketChipVSrc" \
      ${lib.optionalString enableTrace "--trace-fst"} \
      --timing \
      --threads 8 \
      --threads-max-mtasks 8000 \
      -O1 \
      -Wno-WIDTHEXPAND \
      -Wno-LATCH \
      --cc TestBench

    echo "[nix] building verilated C lib"

    # backup srcs
    mkdir -p $out/share
    cp -r obj_dir $out/share/verilated_src

    rm $out/share/verilated_src/*.dat

    # We can't use -C here because VTestBench.mk is generated with relative path
    cd obj_dir
    make -j "$NIX_BUILD_CORES" -f VTestBench.mk libVTestBench

    runHook postBuild
  '';

  hardeningDisable = [ "fortify" ];

  passthru = {
    inherit enableTrace rocket-chip-v-src;
  };

  installPhase = ''
    runHook preInstall

    mkdir -p $out/include $out/lib
    cp *.h $out/include
    cp *.a $out/lib

    runHook postInstall
  '';
}
