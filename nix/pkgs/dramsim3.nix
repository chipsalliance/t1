{ stdenv, fetchFromGitHub, cmake, ninja }:
stdenv.mkDerivation rec {
  pname = "dramsim3";
  version = "fa9a585878e13216bdd98bd00410a13ab1eb5558";
  nativeBuildInputs = [ cmake ninja ];
  src = fetchFromGitHub {
    owner = "sequencer";
    repo = "t1-DRAMsim3";
    rev = version;
    sha256 = "sha256-fXhsfmVGpI1Y5qrJcHD7ZD96Wvkf5tDoPhS1cV0fW2A=";
  };
}

