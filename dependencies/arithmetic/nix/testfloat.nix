{ stdenv, fetchFromGitHub, softfloat }:
stdenv.mkDerivation rec {
  pname = "testfloat";
  version = "bd3e0741bcd3a2c98432c2825671f86745bfca36";
  src = fetchFromGitHub {
    owner = "ucb-bar";
    repo = "berkeley-testfloat-3";
    rev = version;
    sha256 = "sha256-hMSSJc5JFB39U6/6Ls/W5+YqmwoJDVSoG5mNU9g4hAw=";
  };
  buildPhase = ''
    make -C build/Linux-x86_64-GCC SPECIALIZE_TYPE=RISCV SOFTFLOAT_INCLUDE_DIR=${softfloat}/include SOFTFLOAT_LIB=${softfloat}/lib/softfloat.a TESTFLOAT_OPTS="-DFLOAT64 -DFLOAT_ROUND_ODD" testfloat.a
  '';
  installPhase = ''
    mkdir -p $out/lib $out/dist
    cp build/Linux-x86_64-GCC/testfloat.a $out/lib/
    cp -vrT source $out/dist/source
  '';
}
