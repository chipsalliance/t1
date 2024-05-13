{ lib
, python3
, fetchPypi
}:

python3.pkgs.buildPythonPackage rec {
  pname = "marshalparser";
  version = "0.3.4";
  pyproject = true;

  src = fetchPypi {
    inherit pname version;
    hash = "sha256-Zk4AMCG3Y52Ghq1yykLidujGiRU3v8eDM7f/s6QsGqI=";
  };

  nativeBuildInputs = [
    python3.pkgs.setuptools
    python3.pkgs.wheel
  ];

  passthru.optional-dependencies = with python3.pkgs; {
    test = [ pytest ];
  };

  pythonImportsCheck = [ "marshalparser" ];

  meta = with lib; {
    description = "Parser for byte-cache .pyc files";
    homepage = "https://pypi.org/project/marshalparser/";
    license = licenses.mit;
    mainProgram = "marshalparser";
  };
}
