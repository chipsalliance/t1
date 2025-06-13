{
  fetchFromGitHub,
  ocamlPackages,
  z3,
}:
ocamlPackages.buildDunePackage {
  pname = "asl-interpreter";
  version = "unstable-637bfc6-2025-06-04";

  src = fetchFromGitHub {
    owner = "IntelLabs";
    repo = "asl-interpreter";
    rev = "637bfc6989aff4426d9c450f19a1adff022e8170";
    hash = "sha256-ygbGkyR2FAW1lIIlAPyOj3WYI96Yy8vJ2jFmoGPvtC0=";
  };

  minimalOCamlVersion = "4.14.2";

  nativeBuildInputs = with ocamlPackages; [
    menhir
    alcotest
  ];

  buildInputs = [
    z3
  ];

  propagatedBuildInputs = with ocamlPackages; [
    ocamlPackages.z3
    menhirLib
    yojson
    zarith
    dune-site
    linenoise
    ocolor
    odoc
  ];

  buildPhase = ''
    runHook preBuild
    dune build --release ''${enableParallelBuilding:+-j $NIX_BUILD_CORES}
    runHook postBuild
  '';

  checkPhase = ''
    runHook preCheck
    dune test ''${enableParallelBuilding:+-j $NIX_BUILD_CORES}
    runHook postCheck
  '';

  installPhase = ''
    runHook preInstall
    dune install --prefix $out --libdir $OCAMLFIND_DESTDIR
    runHook postInstall
  '';
}
