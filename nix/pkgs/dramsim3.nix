{ stdenv, fetchFromGitHub, cmake, ninja }:
stdenv.mkDerivation rec {
  pname = "dramsim3";
  version = "5a22b79fd985ee826ae8a8ddc362b01c5acdec87";
  nativeBuildInputs = [ cmake ninja ];
  src = fetchFromGitHub {
    owner = "CircuitCoder";
    repo = "dramsim3";
    rev = "5a22b79fd985ee826ae8a8ddc362b01c5acdec87";
    sha256 = "sha256-fXhsfmVGpI1Y5qrJcHD7ZD96Wvkf5tDoPhS1cV0fW2A=";
  };
}

