{ stdenv
, configName
, rtl
, verilator
}:
stdenv.mkDerivation {
  name = "${configName}-verilated";

  src = rtl;

  nativeBuildInputs = [ verilator ];

  buildPhase = ''
    runHook preBuild

    echo "[nix] running verilator"
    verilator --timing --cc TestBench

    echo "[nix] building verilated C lib"

    # backup srcs
    mkdir -p $out/share
    cp -r obj_dir $out/share/verilated_src

    # We can't use -C here because VTestBench.mk is generated with relative path
    cd obj_dir
    make -j $(nproc) -f VTestBench.mk libVTestBench

    runHook postBuild
  '';

  installPhase = ''
    runHook preInstall

    mkdir -p $out/include $out/lib
    cp *.h $out/include
    cp *.a $out/lib

    runHook postInstall
  '';
}
