#!/usr/bin/env bash

set -e


projectDir=${1:-}
[[ -z "$projectDir" ]] && echo "No argv[1]" && exit 1

name=${2:-}
[[ -z "$name" ]] && echo "No argv[2]" && exit 1

if [[ ! -f "build.mill"  ]]; then
  echo "Not running in project root" >&2
  exit 1
fi

nixAddPath() {
  export PATH="$(nix build ".#$1" --no-link --print-out-paths)/bin:$PATH"
}

nixAddPath "mill"
nixAddPath "mill-ivy-fetcher"

echo "Evaluating lock for $name in $projectDir"
cacheDir=$(mktemp -d)
cp -rT "$projectDir" "$cacheDir"/project
mkdir -p "$cacheDir"/cache
chmod -R u+w "$cacheDir"
mif fetch \
  --project-dir "$cacheDir"/project \
  --cache "$cacheDir/cache"
mif codegen --cache "$cacheDir/cache" -o ./dependencies/locks/"$name-lock.nix"
rm -rf "$cacheDir"
