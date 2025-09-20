{ fetchFromGitHub, buildGoModule }:
buildGoModule rec {
  pname = "riscv-vector-tests";
  version = "unstable-2025-04-01";
  src = fetchFromGitHub {
    owner = "chipsalliance";
    repo = pname;
    rev = "d88736d08d7aead7bd3cddc09f64bd5050c7b9e8";
    hash = "sha256-9aAVR6EV/9uXwE0G8XGKIatPx44php3DHJULXGDpZX0=";
  };

  patches = [
    ../patches/riscv-vector-tests/00-remove-vlen-check.patch
  ];

  doCheck = false;

  vendorHash = "sha256-1A5yCj9AJHp9tcUIpLKamXn4Tl3KPFEtzke5X7h6V+4=";

  # Get rid of copying the whole source
  postInstall = ''
    cp -r $src/configs $out/configs
  '';
}
