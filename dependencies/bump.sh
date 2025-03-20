#!/usr/bin/env bash

set -e

if [[ ! -f "build.mill"  ]]; then
  echo "Not running in project root" >&2
  exit 1
fi

mkdir -p ./dependencies/locks
submodules=(
  chisel
  arithmetic
  chisel-interface
  rvdecoderdb
)

usersOpt="$JAVA_TOOL_OPTIONS"

for module in "${submodules[@]}"; do
  echo "Running $module"
  projectDir=$(nix build --no-link --print-out-paths ".#t1.submodules.sources.$module.src")
  ./dependencies/genlock.sh "$projectDir" "$module"
  if [[ "$module" == "chisel" ]]; then
    ivyLocal="$(nix build '.#t1.submodules.ivy-chisel' --no-link --print-out-paths)"
    export JAVA_TOOL_OPTIONS="-Dcoursier.ivy.home=$ivyLocal -Divy.home=$ivyLocal $usersOpt"
  fi
  echo "Done"
done

extraSubmodules=(
  "./dependencies/berkeley-hardfloat"
)
for module in "${extraSubmodules[@]}"; do
  ./dependencies/genlock.sh "$module" "$(basename $module)"
done

unset JAVA_TOOL_OPTIONS
export JAVA_TOOL_OPTIONS="$userOpts"
./prepare-t1.sh
