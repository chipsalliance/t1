{
  python3,
  python3Packages,
  iree,
  python-iree,
  fetchFromGitHub,
}:
let
  iree-turbine-version = "3dedc95d2340a64bd38964f362852cc3a9e6a54a";
in
python3.pkgs.buildPythonPackage {
  pname = "iree-turbine";
  version = iree-turbine-version;

  src = fetchFromGitHub {
    owner = "iree-org";
    repo = "iree-turbine";
    rev = "${iree-turbine-version}";
    hash = "sha256-H2Qz2sIKB0ZCmVsrLiAmSktID7AGqLCqKrKiBj0RrKU=";
  };

  build-system = [
    python3Packages.setuptools
  ];

  dependencies = [
    python3Packages.numpy
    python3Packages.jinja2
    python-iree
    python3Packages.ml-dtypes
  ];
}
