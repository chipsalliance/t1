final: prev:

let
  llvmForRVV_attrName = "llvmPackages_git";
  rv32_pkgs = final.rv32_nixpkgs;
  rv32_buildPkgs = final.rv32_nixpkgs.buildPackages;
in
rec {
  getEnv' =
    key:
    let
      val = builtins.getEnv key;
    in
    if val == "" then builtins.throw "${key} not set or '--impure' not applied" else val;

  espresso = final.callPackage ./pkgs/espresso.nix { };
  dramsim3 = final.callPackage ./pkgs/dramsim3.nix { };
  libspike = final.callPackage ./pkgs/libspike.nix { };
  libspike_interfaces = final.callPackage ../difftest/spike_interfaces { };

  dramsim3-config = ../difftest/config/dramsim3-config.ini;

  buddy-mlir =
    let
      pkgSrc = final.fetchFromGitHub {
        owner = "NixOS";
        repo = "nixpkgs";
        rev = "2a725d40de138714db4872dc7405d86457aa17ad";
        hash = "sha256-WWNNjCSzQCtATpCFEijm81NNG1xqlLMVbIzXAiZysbs=";
      };
      lockedNixpkgs = import pkgSrc { system = final.system; };
    in
    lockedNixpkgs.callPackage ./pkgs/buddy-mlir.nix { python3 = lockedNixpkgs.python312; };

  iree = final.callPackage ./pkgs/iree.nix { };

  python-iree = final.python3Packages.toPythonModule iree;

  iree-turbine = final.callPackage ./pkgs/iree-turbine.nix { };

  riscv-vector-tests = final.callPackage ./pkgs/riscv-vector-tests.nix { };

  snps-fhs-env = final.callPackage ./pkgs/snps-fhs-env.nix { };

  mill =
    let
      jre = final.jdk21;
    in
    (prev.mill.override { inherit jre; }).overrideAttrs rec {
      # Fixed the buggy sorting issue in target resolve
      version = "0.12.8-1-46e216";
      src = final.fetchurl {
        url = "https://repo1.maven.org/maven2/com/lihaoyi/mill-dist/${version}/mill-dist-${version}-assembly.jar";
        hash = "sha256-XNtl9NBQPlkYu/odrR/Z7hk3F01B6Rk4+r/8tMWzMm8=";
      };
      passthru = { inherit jre; };
    };

  # some symbols in newlib libgloss uses ecall, which does not work in emulator
  # emurt provides hand-written implementations for these symbols
  # TODO: Now all the test cases are using `memcpy` provided by newlib. We will eventually want a rvv optimized `memcpy` and add it to our emurt.
  emurt = final.callPackage ../tests/emurt {
    stdenv = rv32_pkgs.stdenv;
    bintools = rv32_buildPkgs.bintools;
  };

  t1-script = final.callPackage ../script { };
  inherit (t1-script) t1-helper ci-helper;

  # stdenv for compiling rvv programs, with ilp32f newlib and clang
  rv32-stdenv = rv32_pkgs.stdenv.override {
    cc =
      let
        major = final.lib.versions.major rv32_buildPkgs.${llvmForRVV_attrName}.release_version;

        # By default, compiler-rt and newlib for rv32 are built with double float point abi by default.
        # We need to override it with `-mabi=ilp32f`

        # compiler-rt requires the compilation flag -fforce-enable-int128, only clang provides that
        compilerrt =
          (rv32_pkgs.${llvmForRVV_attrName}.compiler-rt.override {
            stdenv =
              rv32_pkgs.overrideCC rv32_pkgs.stdenv
                rv32_buildPkgs.${llvmForRVV_attrName}.clangNoCompilerRt;
          }).overrideAttrs
            (oldAttrs: {
              env.NIX_CFLAGS_COMPILE = "-march=rv32imacf_zvl128b_zve32f -mabi=ilp32f";
            });

        newlib = rv32_pkgs.stdenv.cc.libc.overrideAttrs (oldAttrs: {
          CFLAGS_FOR_TARGET = "-march=rv32imacf_zvl128b_zve32f -mabi=ilp32f";
        });

        llvmPackages = rv32_buildPkgs.${llvmForRVV_attrName};
        patchedCC =
          let
            llvmSrc = prev.fetchFromGitHub {
              owner = "topperc";
              repo = "llvm-project";
              rev = "bb123ff9401b517d877de4ed6fd9ea61edf49dbb";
              hash = "sha256-6ca5FhSsG5Qik7wi6Vn3OmUmKR/hULSDniu4JLo+5jM=";
            };
          in
          llvmPackages.clang-unwrapped.override {
            monorepoSrc = llvmSrc;
            libllvm = llvmPackages.libllvm.override { monorepoSrc = llvmSrc; };
          };
      in
      rv32_buildPkgs.wrapCCWith rec {
        cc = patchedCC;
        libc = newlib;
        bintools = rv32_pkgs.stdenv.cc.bintools.override {
          inherit libc; # we must keep consistency of bintools libc and compiler libc
          inherit (rv32_buildPkgs.${llvmForRVV_attrName}.bintools) bintools;
        };

        # common steps to produce clang resource directory
        extraBuildCommands = ''
          rsrc="$out/resource-root"
          mkdir "$rsrc"
          ln -s "${cc.lib}/lib/clang/${major}/include" "$rsrc"
          echo "-resource-dir=$rsrc" >> $out/nix-support/cc-cflags
          ln -s "${compilerrt}/lib" "$rsrc/lib"
          ln -s "${compilerrt}/share" "$rsrc/share"
        '';

        # link against emurt
        extraPackages = [ final.emurt ];
        nixSupport.cc-cflags = [ "-lemurt" ];
      };
  };

  iree-runtime = final.callPackage ./pkgs/iree-runtime.nix { };

  riscv-tests = final.pkgsCross.riscv32-embedded.stdenv.mkDerivation rec {
    pname = "riscv-tests";
    version = "7878085d2546af0eb7af72a1df00996d5d8c43fb";
    src = final.fetchFromGitHub {
      owner = "riscv-software-src";
      repo = "riscv-tests";
      rev = "${version}";
      hash = "sha256-CruSrXVO5Qlk63HPBVbwzl/RdxAAl2bknWawDHJwEKY=";
    };

    postUnpack = ''
      rm -rf $sourceRoot/env
      cp -r ${../tests/riscv-test-env} $sourceRoot/env
    '';

    enableParallelBuilding = true;

    configureFlags = [
      # to match rocket-tools path
      "--prefix=${placeholder "out"}/riscv32-unknown-elf"
    ];
    buildPhase = "make RISCV_PREFIX=riscv32-none-elf-";
    installPhase = ''
      runHook preInstall
      make install
      mkdir -p $out/debug/
      cp debug/*.py $out/debug/
      runHook postInstall
    '';
  };

  t1 = final.callPackage ./t1 { };
}
