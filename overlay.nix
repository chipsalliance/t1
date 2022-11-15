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
      preInstall = ''
        mkdir -p $out/src
        cp -r * $out/src
      '';
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
    ${my-cc-wrapper}/bin/clang --target=riscv32 -fuse-ld=lld -L${rv32-compilerrt}/lib/riscv32 -L${rv32-musl}/lib $@
  '';
in
{
  inherit myLLVM rv32-compilerrt rv32-musl rv32-clang my-cc-wrapper;

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

  circt = final.stdenv.mkDerivation {
    pname = "circt";
    version = "r4396.ce85204ca";
    nativeBuildInputs = with final; [ cmake ninja python3 git ];
    src = final.fetchFromGitHub {
      owner = "llvm";
      repo = "circt";
      rev = "6937e9b8b5e2a525f043ab89eb16812f92b42c62";
      sha256 = "sha256-Lpu8J9izWvtYqibJQV0xEldk406PJobUM9WvTmNS3g4=";
      fetchSubmodules = true;
    };
    cmakeFlags = [
      "-S/build/source/llvm/llvm"
      "-DLLVM_ENABLE_PROJECTS=mlir"
      "-DBUILD_SHARED_LIBS=OFF"
      "-DLLVM_STATIC_LINK_CXX_STDLIB=ON"
      "-DLLVM_ENABLE_ASSERTIONS=ON"
      "-DLLVM_BUILD_EXAMPLES=OFF"
      "-DLLVM_ENABLE_BINDINGS=OFF"
      "-DLLVM_ENABLE_OCAMLDOC=OFF"
      "-DLLVM_OPTIMIZED_TABLEGEN=ON"
      "-DLLVM_EXTERNAL_PROJECTS=circt"
      "-DLLVM_EXTERNAL_CIRCT_SOURCE_DIR=/build/source"
      "-DLLVM_BUILD_TOOLS=ON"
    ];
    installPhase = ''
      mkdir -p $out/bin
      mv bin/firtool $out/bin/firtool
    '';
  };

  verilator = prev.verilator.overrideAttrs (old: {
    src = final.fetchFromGitHub {
      owner = "verilator";
      repo = "verilator";
      rev = "2e4f5c863ffa6ab1afca883559ee5a6ca989e9d7";
      sha256 = "sha256-jANlrumSGISeNB7MDrXqY2G6jMgrPApzk/1SoO92N2Y=";
    };
  });

  mill = prev.mill.override { jre = final.openjdk18; };
}
