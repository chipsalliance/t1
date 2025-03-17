#!/usr/bin/env bash

set -e

ivyLocal=$(nix build '.#t1.submodules.ivyLocalRepo' --no-link --print-out-paths --max-jobs auto)
export JAVA_TOOL_OPTIONS="-Dcoursier.ivy.home=$ivyLocal -Divy.home=$ivyLocal $JAVA_TOOL_OPTIONS"
./dependencies/genlock.sh "$PWD" "t1"
