{ fetchFromGitHub, buildGoModule }:
buildGoModule rec {
  pname = "riscv-vector-tests";
  version = "unstable-2025-04-01";
  src = fetchFromGitHub {
    owner = "chipsalliance";
    repo = pname;
    rev = "116c90851e1bae067f8b11b51bdee858e8dacafa";
    hash = "sha256-mjFPGgaceZtqPVkHQ2/1Brv6LS6UxWEi5uTyHMq3WS8=";
  };

  doCheck = false;

  vendorHash = "sha256-1A5yCj9AJHp9tcUIpLKamXn4Tl3KPFEtzke5X7h6V+4=";

  # Get rid of copying the whole source
  postInstall = ''
    cp -r $src/configs $out/configs
  '';
}
