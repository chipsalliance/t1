{ fetchFromGitHub, fetchgit, fetchzip, stdenv, curl, texinfo, bison, flex, gmp, mpfr, libmpc, python3, perl, flock, expat }:
let
  binutilsSrc = fetchgit {
    url = "https://sourceware.org/git/binutils-gdb.git";
    rev = "675b9d612cc59446e84e2c6d89b45500cb603a8d"; #v2.41
    hash = "sha256-bbzw4QFdlTQIknRb7rHtK9a7kWsOBAKHvGeUKoDtGB4=";
  };
  gccSrc = fetchgit {
    url = "https://gcc.gnu.org/git/gcc.git";
    rev = "c891d8dc23e1a46ad9f3e757d09e57b500d40044"; # 13.2.0
    hash = "sha256-AAu/jE3MlMgvd+xagn9ujJ8PpKJZ16iZXhU9QxNRZSk=";
  };
  gdbSrc = fetchgit {
    url = "https://sourceware.org/git/binutils-gdb.git";
    rev = "662243de0e14a4945555a480dca33c0e677976eb";
    hash = "sha256-LgBvtrFsw/s7cKmb3s/HUK22PWuRuqlhS0Y7WWgzzs4=";
  };
  newlibSrc = fetchzip {
    url = "https://sourceware.org/pub/newlib/newlib-4.3.0.20230120.tar.gz";
    hash = "sha256-4IJJ2WiSU06+N63Yev/lmkmjVc/cuyIGQnvt6D+1piI=";
  };
in
stdenv.mkDerivation rec {
  pname = "riscv-gnu-toolchain";
  version = "unstable-2023-10-13";
  src = fetchFromGitHub {
    owner = "riscv-collab";
    repo = pname;
    rev = "6b1324367b879a9b89437846827b48151b26b412";
    sha256 = "sha256-CKGVp/icKi1jqWDtAKsRsr/6BwTQWbVU4y5AewkGzFU=";
  };

  postUnpack = ''
    cp -pr --reflink=auto -- ${binutilsSrc} binutils
    binutilsSrc=$(realpath ./binutils)
    cp -pr --reflink=auto -- ${gccSrc} gcc
    gccSrc=$(realpath ./gcc)
    cp -pr --reflink=auto -- ${newlibSrc} newlib
    newlibSrc=$(realpath ./newlib)
    cp -pr --reflink=auto -- ${gdbSrc} gdb
    gdbSrc=$(realpath ./gdb)

    chmod -R u+w -- ./*
  '';

  nativeBuildInputs = [
    curl
    perl
    python3
    texinfo
    bison
    flex
    gmp
    mpfr
    libmpc

    flock # required for installing file
    expat # glibc
  ];

  enableParallelBuilding = true;

  # Specify the source directory here to avoid build system use git submodule
  preConfigure = ''
    configureFlagsArray+=(
      --with-binutils-src="$binutilsSrc"
      --with-gcc-src="$gccSrc"
      --with-gdb-src="$gdbSrc"
      --with-newlib-src="$newlibSrc"
    )
  '';

  configureFlags = [
    "--with-arch=rv32gcv"
    "--with-abi=ilp32f"
  ];

  postConfigure = ''
    # nixpkgs will set those value to bare string "ar", "objdump"...
    # however we are cross-compiling, we must let $CC to determine which bintools to use.
    unset AR AS LD OBJCOPY OBJDUMP
  '';

  # RUN: make
  makeFlags = [
    # Install to nix out dir
    "INSTALL_DIR=${placeholder "out"}"
  ];

  # -Wno-format-security
  hardeningDisable = [ "format" ];

  dontPatchELF = true;
  dontStrip = true;
}
