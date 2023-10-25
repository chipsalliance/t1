{ lib
, fetchFromGitHub
, mill
, fetchMillDeps
, millSetupHook
, jq
, # chisel deps
  espresso
, circt
, protobuf
, antlr4
, # emulator deps
  cmake
, libargs
, spdlog
, fmt
, libspike
, nlohmann_json
, ninja
, verilator
, zlib
, # Try speed up compilation
  llvmPackages_14
, # Compile options
  emulatorTypes ? [ "v1024l8b2-test" "v1024l8b2-test-trace" ]
, emulatorSrc ? null
}:

assert lib.assertMsg ((builtins.typeOf emulatorTypes) == "list") "`emulatorTypes` is not a `list`";

let
  t1 = (fetchFromGitHub {
    owner = "chipsalliance";
    repo = "t1";
    rev = "a8c7e76545c3abd7520440a8d572e4f6b5e2aa77";
    hash = "sha256-PFi4BEJdeTNwYar2QGXhtqIo1WYPk8lFLJ2eJy/NGZ0=";
    fetchSubmodules = true;
  }).overrideAttrs (_: {
    GIT_CONFIG_COUNT = 1;
    GIT_CONFIG_KEY_0 = "url.https://github.com/.insteadOf";
    GIT_CONFIG_VALUE_0 = "git@github.com:";
  });
in
llvmPackages_14.stdenv.mkDerivation rec {
  version = "unstable-2023-10";
  pname = "emulator";

  src = if (builtins.typeOf emulatorSrc != "null") then emulatorSrc else t1;

  millDeps = fetchMillDeps {
    inherit src pname;
    millDepsHash = "sha256-7opXn973oEMRKSAMugIpXjCUrI0qS5wpbd8R5jTE8Uo=";
  };

  nativeBuildInputs = [
    mill
    millSetupHook
    jq
    espresso
    circt
    protobuf
    antlr4
    cmake
    libargs
    spdlog
    fmt
    libspike
    nlohmann_json
    ninja
    verilator
    zlib
  ];

  # Do not automatically run cmake, mill will handle this
  dontUseCmakeConfigure = true;

  buildPhase = ''
    # Map[String, String](
    #    "$emulatorType" -> "$emulatorBinPath"
    # )
    declare -A outputsArray

    ${lib.toShellVar "emulatorTypes" emulatorTypes}

    # Mill will write rubbish into stdout in a fresh environment,
    # so we need to do a simple warm up here to let it generate cache.
    mill -i resolve emulator._ &> /dev/null

    for t in ''${emulatorTypes[@]}; do
      echo "[nix] building emulator $t"
      local path=$(mill -i show "emulator[$t].elf" | jq --raw-output '. | split(":") | .[3]')
      [[ -z "$path" ]] && echo "[build] fail to find path for emualtor $t" && exit 1
      outputsArray+=( ["$t"]="$path" )
    done
  '';

  installPhase = ''
    mkdir -p $out/bin

    for t in ''${!outputsArray[@]}; do
      mv ''${outputsArray[''${t}]} $out/bin/emulator-$t
    done
  '';
}
