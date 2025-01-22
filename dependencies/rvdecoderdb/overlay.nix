final: prev:
{
  mill = (prev.mill.overrideAttrs (oldAttrs: rec {
    version = "0.11.2";
    src = prev.fetchurl {
      url = "https://github.com/com-lihaoyi/mill/releases/download/${version}/${version}-assembly";
      hash = "sha256-7RYMj/vfyzBQhZUpWzEaZYN27ZhYCRyKhQUhlH8tE0U=";
    };
  })).override {
    jre = final.openjdk20; 
  };
  espresso = final.callPackage ./nix/espresso.nix { };
}
