{ pkgs ? import <nixpkgs> {}}:

let
  myLLVM = pkgs.llvmPackages_14;
  espresso = pkgs.stdenv.mkDerivation rec {
    pname = "espresso";
    version = "2.4";
    nativeBuildInputs = [ pkgs.cmake ];
    src = pkgs.fetchFromGitHub {
      owner = "chipsalliance";
      repo = "espresso";
      rev = "v${version}";
      sha256 = "sha256-z5By57VbmIt4sgRgvECnLbZklnDDWUA6fyvWVyXUzsI=";
    };
  };
  circt = pkgs.stdenv.mkDerivation {
    pname = "circt";
    version = "r4396.ce85204ca";
    nativeBuildInputs = with pkgs; [ cmake ninja python3 git ];
    src = pkgs.fetchFromGitHub {
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

  verilator = pkgs.verilator.overrideAttrs (old: {
    src = pkgs.fetchFromGitHub {
      owner = "verilator";
      repo = "verilator";
      rev = "2e4f5c863ffa6ab1afca883559ee5a6ca989e9d7";
      sha256 = "sha256-jANlrumSGISeNB7MDrXqY2G6jMgrPApzk/1SoO92N2Y=";
    };
  });

  # nix cc-wrapper will add --gcc-toolchain to clang flags. However, when we want to use
  # our custom libc and compilerrt, clang will only search these libs in --gcc-toolchain 
  # folder. To avoid this wierd behavior of clang, we need to remove --gcc-toolchain options
  # from cc-wrapper
  my-cc-wrapper = let cc = myLLVM.clang; in pkgs.runCommand "cc" {} ''
    mkdir -p "$out"
    cp -rT "${cc}" "$out"
    chmod -R +w "$out"
    sed -i 's/--gcc-toolchain=[^[:space:]]*//' "$out/nix-support/cc-cflags"
    sed -i 's|${cc}|${placeholder "out"}|g' "$out"/bin/* "$out"/nix-support/*
  '';

in pkgs.mkShellNoCC {
    name = "vector";
    buildInputs = with pkgs; [
      myLLVM.llvm
      myLLVM.bintools
      my-cc-wrapper

      jdk mill python3
      parallel protobuf ninja verilator antlr4 numactl dtc glibc_multi cmake
      espresso
      circt

      git cacert # make cmake fetchContent happy
      fmt glog
    ];
    shellHook = ''
      export NIX_CC=" "
      # because we removed --gcc-toolchain from cc-wrapper, we need to add gcc lib path back
      export NIX_LDFLAGS_FOR_TARGET="$NIX_LDFLAGS_FOR_TARGET -L${pkgs.gccForLibs.lib}/lib"
    '';
  }
