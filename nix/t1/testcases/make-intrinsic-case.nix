{ stdenvNoCC, jq, writeText, rv32-clang, rv32-newlib, llvmForDev }:

{ caseName, compileFlags ? [ ], xLen ? 32, vLen ? 1024, fp ? false, ... }@inputs:
let
  caseConfigFile = writeText "${caseName}-intrinsic.json" (builtins.toJSON {
    name = "${caseName}";
    type = "intrinsic";
    inherit xLen vLen fp;
  });
in
stdenvNoCC.mkDerivation ({
  name = "${caseName}-intrinsic";

  unpackPhase = ''
    if [ -z "''${srcs:-}" ]; then
        if [ -z "''${src:-}" ]; then
            echo 'variable $src or $srcs should point to the source'
            exit 1
        fi
        srcs="$src"
    fi
  '';

  compileFlags = [
    "-mabi=ilp32f"
    "-march=rv32gcv"
    "-mno-relax"
    "-static"
    "-mcmodel=medany"
    "-fvisibility=hidden"
    "-nostdlib"
    "-Wl,--entry=start"
    "-fno-PIC"
    "-I${rv32-newlib}/${rv32-newlib.passthru.incdir}" # Needed for gnu/stub-32.h
  ];

  nativeBuildInputs = [
    jq
    rv32-clang
    llvmForDev.bintools
  ];

  buildPhase = ''
    clang-rv32 $compileFlags $srcs -o $name.elf
  '';

  installPhase = ''
    mkdir -p $out/bin
    cp $name.elf $out/bin
    jq ".+={\"elf\": {\"path\": \"$out/bin/$name.elf\"}}" ${caseConfigFile} > $out/$name.json
  '';

  dontPatchELF = true;
  dontStrip = true;
  dontShrinkRPATH = true;
} // inputs)
