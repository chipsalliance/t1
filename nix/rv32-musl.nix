{ fetchFromGitHub, llvmForDev, rv32-compilerrt }:
let
  pname = "musl";
  version = "a167b20fd395a45603b2d36cbf96dcb99ccedd60";
  src = fetchFromGitHub {
    owner = "sequencer";
    repo = "musl";
    rev = "a167b20fd395a45603b2d36cbf96dcb99ccedd60";
    sha256 = "sha256-kFOTlJ5ka5h694EBbwNkM5TLHlFg6uJsY7DK5ImQ8mY=";
  };
in
llvmForDev.stdenv.mkDerivation {
  inherit src pname version;
  nativeBuildInputs = [ llvmForDev.bintools ];
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
}

