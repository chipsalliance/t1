{ fetchFromGitHub, buildGoModule }:
buildGoModule rec {
  pname = "riscv-vector-tests";
  version = "unstable-2025-04-01";
  src = fetchFromGitHub {
    owner = "chipsalliance";
    repo = pname;
    rev = "1a1aa4d4eab9c8372b2f4c2f9f1b906859c0df44";
    hash = "sha256-S2tJFgAWnKs9gRNT2ui04PHIzCbLzBahDCDjOtIad/s=";
  };

  doCheck = false;

  vendorHash = "sha256-1A5yCj9AJHp9tcUIpLKamXn4Tl3KPFEtzke5X7h6V+4=";

  # Get rid of copying the whole source
  postInstall = ''
    cp -r $src/configs $out/configs
  '';
}
