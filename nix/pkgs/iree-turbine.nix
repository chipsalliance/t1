{
  python3,
  python3Packages,
  iree,
  python-iree,
  fetchFromGitHub,
}:
let
  iree-turbine-version = "b99b395aef8423d07e16a229751c85dc857cb719";
in
python3.pkgs.buildPythonPackage {
  pname = "iree-turbine";
  version = iree-turbine-version;

  src = fetchFromGitHub {
    owner = "iree-org";
    repo = "iree-turbine";
    rev = "${iree-turbine-version}";
    hash = "sha256-WcteI9Zh/oiOQhl6Cgs2b9KXQPao+WnfVZEVuT+Dtlc=";
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
