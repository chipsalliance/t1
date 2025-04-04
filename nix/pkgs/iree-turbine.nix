{
  python3,
  python3Packages,
  iree,
  python-iree,
  fetchFromGitHub,
}:
let
  iree-turbine-version = "3.3.0";
in
python3.pkgs.buildPythonPackage {
  pname = "iree-turbine";
  version = iree-turbine-version;

  src = fetchFromGitHub {
    owner = "iree-org";
    repo = "iree-turbine";
    rev = "v${iree-turbine-version}";
    hash = "sha256-ccBDUK2t8RXDKsQP9wOzvW/tz8RNBadsDwtONRSc0S0=";
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
