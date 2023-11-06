{ fetchFromGitHub
, fetchPypi
, fetchpatch
, buildPythonPackage

  # build deps
, pip
, cmake
, zlib
, setuptools

  # runtime deps
, importlib-resources
, cffi
}:

let
  pylibfst = buildPythonPackage rec {
    pname = "pylibfst";
    version = "0.2.0";
    format = "pyproject";

    src = fetchPypi {
      inherit pname version;
      hash = "sha256-/aAEfGuuJgEKQuXgdH67P3mo7wjFBWNRksl0Up0ylSU=";
    };
    dontUseCmakeConfigure = true;
    nativeBuildInputs = [
      cmake
      setuptools
    ];

    buildInputs = [
      zlib
    ];

    propagatedBuildInputs = [
      cffi
    ];
  };
  lark-parser = buildPythonPackage rec {
    pname = "lark-parser";
    version = "0.12.0";
    format = "setuptools";

    src = fetchPypi {
      inherit pname version;
      hash = "sha256-FZZ9sfEhQBPcplsRgHRQR7m+RX1z2iJPzaPZ3U6WoTg=";
    };
  };
in
buildPythonPackage {
  pname = "wal-lang";
  version = "unstable-2023-08-12";
  format = "pyproject";

  src = fetchFromGitHub {
    owner = "ics-jku";
    repo = "wal";
    rev = "06498c3f5341ce687a37ad52647f300b72dff52a";
    hash = "sha256-gdgaqpbWtc66FEwhTV0AI88TLyfUheBQo4AuZgNyTKY=";
  };

  patches = [
    (fetchpatch {
      url = "https://github.com/ics-jku/wal/pull/24.diff";
      hash = "sha256-mI66M+l3gBQJiv+xYl0Y+T0UCSOHUvHn4STEOJ8UmI8=";
    })
  ];

  nativeBuildInputs = [
    pip
    setuptools
  ];

  propagatedBuildInputs = [
    importlib-resources
    lark-parser
    pylibfst
  ];

  doCheck = false;
}
