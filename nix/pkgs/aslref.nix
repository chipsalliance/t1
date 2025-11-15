{
  fetchFromGitHub,
  ocamlPackages,
}:

ocamlPackages.buildDunePackage {
  pname = "aslref";
  version = "7.58-unstable-73140600";

  src = fetchFromGitHub {
    owner = "herd";
    repo = "herdtools7";
    rev = "731406005260d9c131b370febb5c5bb6221db695";
    hash = "sha256-0R+R5WZFAHwLh7hQbobMCLKZTy9dPMGT43f9kd1hiiE=";
  };

  nativeBuildInputs = with ocamlPackages; [
    ocaml
    dune_3
    menhir
    zarith
    menhirLib
  ];

  buildInputs = with ocamlPackages; [
    findlib
    zarith
    menhirLib
  ];

  configurePhase = ''
    runHook preConfigure

    # Disable dune cache to avoid permission issues
    export DUNE_CACHE=disabled
    export DUNE_CACHE_ROOT=/tmp/dune-cache-$$

    runHook postConfigure
  '';

  buildPhase = ''
    runHook preBuild

    # Generate version information in the project root
    bash ./version-gen.sh $out

    # Disable dune cache to avoid permission issues
    export DUNE_CACHE=disabled

    # Build only the core library and executables, skip tests and optional components
    dune build -j "$NIX_BUILD_CORES" --profile release --cache disabled asllib/aslref.exe

    runHook postBuild
  '';

  installPhase = ''
    runHook preInstall

    mkdir -p $out/bin
    cp _build/default/asllib/aslref.exe $out/bin/aslref
    chmod +x $out/bin/aslref

    runHook postInstall
  '';
}
