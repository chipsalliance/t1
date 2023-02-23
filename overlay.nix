final: prev:

let
  myLLVM = final.llvmPackages_14;  # do not get into nixpkgs namespace

  rv32-compilerrt = let
    pname = "rv-compilerrt";
    version = myLLVM.llvm.version;
    src = final.fetchFromGitHub {
      owner = "llvm";
      repo = "llvm-project";
      rev = version;
      sha256 = "sha256-vffu4HilvYwtzwgq+NlS26m65DGbp6OSSne2aje1yJE=";
    };
  in
    final.llvmPackages_14.stdenv.mkDerivation {
      sourceRoot = "${src.name}/compiler-rt";
      inherit src version pname;
      nativeBuildInputs = [ final.cmake final.python3 final.glibc_multi ];
      cmakeFlags = [
          "-DCOMPILER_RT_BUILD_LIBFUZZER=OFF"
          "-DCOMPILER_RT_BUILD_SANITIZERS=OFF"
          "-DCOMPILER_RT_BUILD_PROFILE=OFF"
          "-DCOMPILER_RT_BUILD_MEMPROF=OFF"
          "-DCOMPILER_RT_BUILD_ORC=OFF"
          "-DCOMPILER_RT_BUILD_BUILTINS=ON"
          "-DCOMPILER_RT_BAREMETAL_BUILD=ON"
          "-DCOMPILER_RT_INCLUDE_TESTS=OFF"
          "-DCOMPILER_RT_HAS_FPIC_FLAG=OFF"
          "-DCOMPILER_RT_DEFAULT_TARGET_ONLY=On"
          "-DCOMPILER_RT_OS_DIR=riscv32"
          "-DCMAKE_BUILD_TYPE=Release"
          "-DCMAKE_SYSTEM_NAME=Generic"
          "-DCMAKE_SYSTEM_PROCESSOR=riscv32"
          "-DCMAKE_TRY_COMPILE_TARGET_TYPE=STATIC_LIBRARY"
          "-DCMAKE_SIZEOF_VOID_P=8"
          "-DCMAKE_ASM_COMPILER_TARGET=riscv32-none-elf"
          "-DCMAKE_C_COMPILER_TARGET=riscv32-none-elf"
          "-DCMAKE_C_COMPILER_WORKS=ON"
          "-DCMAKE_CXX_COMPILER_WORKS=ON"
          "-DCMAKE_C_COMPILER=clang"
          "-DCMAKE_CXX_COMPILER=clang++"
          "-Wno-dev"
      ];
      CMAKE_C_FLAGS = "-nodefaultlibs -fno-exceptions -mno-relax -Wno-macro-redefined -fPIC";
    };

  rv32-musl = let
    pname = "musl";
    version = "a167b20fd395a45603b2d36cbf96dcb99ccedd60";
    src = final.fetchFromGitHub {
      owner = "sequencer";
      repo = "musl";
      rev = "a167b20fd395a45603b2d36cbf96dcb99ccedd60";
      sha256 = "sha256-kFOTlJ5ka5h694EBbwNkM5TLHlFg6uJsY7DK5ImQ8mY=";
    };
  in
    final.llvmPackages_14.stdenv.mkDerivation {
      inherit src pname version;
      nativeBuildInputs = [ final.llvmPackages_14.bintools ];
      configureFlags = [
        "--target=riscv32-none-elf"
        "--enable-static"
        "--syslibdir=${placeholder "out"}/lib"
      ];
      LIBCC = "-lclang_rt.builtins-riscv32";
      CFLAGS = "--target=riscv32 -mno-relax -nostdinc";
      LDFLAGS = "-fuse-ld=lld --target=riscv32 -nostdlib -L${rv32-compilerrt}/lib/riscv32";
      dontDisableStatic = true;
      dontAddStaticConfigureFlags = true;
      NIX_DONT_SET_RPATH = true;
    };

  # nix cc-wrapper will add --gcc-toolchain to clang flags. However, when we want to use
  # our custom libc and compilerrt, clang will only search these libs in --gcc-toolchain 
  # folder. To avoid this wierd behavior of clang, we need to remove --gcc-toolchain options
  # from cc-wrapper
  my-cc-wrapper = let cc = myLLVM.clang; in final.runCommand "my-cc-wrapper" {} ''
    mkdir -p "$out"
    cp -rT "${cc}" "$out"
    chmod -R +w "$out"
    sed -i 's/--gcc-toolchain=[^[:space:]]*//' "$out/nix-support/cc-cflags"
    sed -i 's|${cc}|${placeholder "out"}|g' "$out"/bin/* "$out"/nix-support/*
    cat >> $out/nix-support/setup-hook <<-EOF
      export NIX_LDFLAGS_FOR_TARGET="$NIX_LDFLAGS_FOR_TARGET -L${prev.gccForLibs.lib}/lib"
    EOF
  '';

  rv32-clang = final.writeShellScriptBin "clang-rv32" ''
    ${my-cc-wrapper}/bin/clang --target=riscv32 -fuse-ld=lld -L${rv32-compilerrt}/lib/riscv32 -L${rv32-musl}/lib "$@"
  '';

  libspike = let
    version = "1.1.0";
    pname = "libspike";
    cmakeConfig = ''
      add_library(libspike STATIC IMPORTED GLOBAL)
      set_target_properties(libspike PROPERTIES
        IMPORTED_LOCATION "${placeholder "out"}/lib/libriscv.so")
      target_include_directories(libspike INTERFACE
        "${placeholder "out"}/include"
        "${placeholder "out"}/include/riscv"
        "${placeholder "out"}/include/fesvr"
        "${placeholder "out"}/include/softfloat"
      )
    '';
  in
    final.stdenv.mkDerivation {
      inherit version pname cmakeConfig;
      enableParallelBuilding = true;
      nativeBuildInputs = [ final.dtc ];
      src = final.fetchFromGitHub {
        owner = "riscv";
        repo = "riscv-isa-sim";
        rev = "ab3225a3ff687fda8b4180f9e4e0949a400d1247";
        sha256 = "sha256-2cC2goTmxWnkTm3Tq08R8YkkuI2Fj8fRvpEPVZ5JvUI=";
      };
      configureFlags = [
        "--enable-commitlog"
      ];
      installPhase = ''
        runHook preInstall
        mkdir -p $out/include/{riscv,fesvr,softfloat} $out/lib $out/lib/cmake/libspike
        cp riscv/*.h $out/include/riscv
        cp fesvr/*.h $out/include/fesvr
        cp softfloat/*.h $out/include/softfloat
        cp config.h $out/include
        cp *.so $out/lib
        echo "$cmakeConfig" > $out/lib/cmake/libspike/libspike-config.cmake
        runHook postInstall
      '';
    };
in
{
  inherit myLLVM rv32-compilerrt rv32-musl rv32-clang my-cc-wrapper libspike;

  espresso = final.stdenv.mkDerivation rec {
    pname = "espresso";
    version = "2.4";
    nativeBuildInputs = [ final.cmake ];
    src = final.fetchFromGitHub {
      owner = "chipsalliance";
      repo = "espresso";
      rev = "v${version}";
      sha256 = "sha256-z5By57VbmIt4sgRgvECnLbZklnDDWUA6fyvWVyXUzsI=";
    };
  };

  buddy-mlir = final.callPackage ./nix/buddy-mlir.nix {};

  mill = prev.mill.override { jre = final.openjdk19; };
}
