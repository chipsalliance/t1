{ lib
, runCommandLocal
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
  emulatorTypes ? [ "v1024l8b2-test" ]
}:

assert lib.assertMsg ((builtins.typeOf emulatorTypes) == "list") "`emulatorTypes` is not a `list`";

let
  srcs = [
    ../build.sc
    ../common.sc
    ../v
    ../run
    ../emulator
    ../elaborator
    ../dependencies
    ../configs
  ];
  # We can simpliy reference the whole repository, but it will let the emulator build again and again
  # even when we are editing GitHub Action workflow.
  pureEmulatorSrc = runCommandLocal "prepareEmulatorSrc" { inherit srcs; } ''
    mkdir -p $out && cd $out

    srcArray=( $srcs )
    for src in ''${srcArray[@]}; do
      dest="$(stripHash "$src")"
      cp -pr --reflink=auto -- "$src" "$dest"
    done
  '';
in
llvmPackages_14.stdenv.mkDerivation rec {
  version = "unstable-2023-10";
  pname = "emulator";
  src = pureEmulatorSrc;

  millDeps = fetchMillDeps {
    inherit pname;
    src = pureEmulatorSrc;
    millDepsHash = "sha256-Z/CobcyBWB3y81iT+IFVapRn1gJh7ky71es11a4NvCU=";
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

    for t in ''${emulatorTypes[@]}; do
      echo "Building emulator $t"
      local path=$(mill -i show "emulator[$t].elf" | jq --raw-output '. | split(":") | .[3]')
      [[ -z "$path" ]] && echo "Compilation fail" && exit 1
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
