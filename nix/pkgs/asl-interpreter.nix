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
    rev = "d07075891e6b458f0a7ccf20dc83c2f148627db6";
    hash = "sha256-oiThDEyRUFd1/yGgsYh9lZCo8gvqb9zO0iZ4LgWX96k=";
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

  fixupPhase = ''
    mkdir -p $out/include $out/lib
    cp -r "$OCAMLFIND_DESTDIR"/asli/runtime_include/* "$out"/include/
    cp -r "$OCAMLFIND_DESTDIR"/asli/runtime/* "$out"/lib/
  '';
}
