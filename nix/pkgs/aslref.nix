{
  fetchFromGitHub,
  ocamlPackages,
}:

ocamlPackages.buildDunePackage {
  pname = "aslref";
  version = "7.57+dev";

  src = fetchFromGitHub {
    owner = "herd";
    repo = "herdtools7";
    rev = "e0e3cf84e130965775689c33f2696fe8a0d7a700";
    hash = "sha256-6SW44j/yBNoz8x9oYg9G3feFwtP6YGkbxP5gOpm3qgI=";
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
    dune build --profile release --cache disabled asllib/aslref.exe asllib/bundler.exe

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
