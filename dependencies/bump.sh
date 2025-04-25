#!/usr/bin/env bash

set -e

if [[ ! -f "build.mill"  ]]; then
  echo "Not running in project root" >&2
  exit 1
fi

# Bump remote source
mkdir -p ./dependencies/locks
bumpScript=(
  "t1.submodules.ivy-chisel.bump"
  "t1.submodules.ivy-chisel-interface.bump"
  "t1.submodules.ivy-arithmetic.bump"
  "t1.submodules.ivy-hardfloat.bump"
  "t1.submodules.ivy-omlib.bump"
  "t1.elaborator.bump"
)

for script in "${bumpScript[@]}"; do
  # Open a new shell to avoid environment pollution
  ( nix run ".#$script" -j auto -- --force)
done
