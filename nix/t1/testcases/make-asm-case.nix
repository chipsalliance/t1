{ stdenvNoCC, rv32-clang, rv32-newlib, llvmForDev }:

{ caseName, compileFlags ? [ ], ... }@inputs:
stdenvNoCC.mkDerivation ({
  name = "${caseName}-asm";

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
  ];

  nativeBuildInputs = [
    rv32-clang
    llvmForDev.bintools
  ];

  buildPhase = ''
    clang-rv32 $compileFlags $srcs -o $name.elf
  '';

  installPhase = ''
    mkdir -p $out/bin
    cp $name.elf $out/bin
  '';

  dontPatchELF = true;
  dontStrip = true;
  dontShrinkRPATH = true;
} // inputs)
