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
  pyproject = true;

  dependencies = [
    python3Packages.numpy
    python3Packages.jinja2
    python-iree
    python3Packages.ml-dtypes
    python3Packages.typing-extensions
  ];

  # The upstream iree-turbine setup.py explicitly requires 'iree-base-compiler' and 'iree-base-runtime'.
  # In our Nix environment, these are provided by the 'python-iree' module (from iree.nix), which
  # manually installs the IREE Python bindings into site-packages.
  # Since our manual installation doesn't generate the PEP 508 metadata (dist-info) that
  # pythonRuntimeDepsCheck looks for, we must patch setup.py to remove these requirements to
  # avoid build failures, while still ensuring the actual code is available via 'python-iree'.
  postPatch = ''
    sed -i '/iree-base-compiler/d' setup.py
    sed -i '/iree-base-runtime/d' setup.py
  '';
}
