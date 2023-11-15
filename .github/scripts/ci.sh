NIX_FILE="./nix/rvv-testcase-prebuilt.nix"
NIX_EXPR_PREFIX="with import <nixpkgs> {}; let pkg = callPackage $NIX_FILE {}; in"

nix_eval() {
  local expr="$@"
  nix eval --impure --raw --expr "$NIX_EXPR_PREFIX $expr"
}

get_src_url() {
  nix_eval "pkg.src.url"
}

get_output_hash() {
  nix_eval "pkg.src.outputHash"
}

get_version() {
  nix_eval "pkg.version"
}

nix_hash_file() {
  local filepath=$1; shift
  # base32 is most used encoding in Nixpkgs
  nix hash file --base32 --type sha256 --sri $filepath
}

# nix hash path create a NAR format for the given path then hash it
nix_hash_path() {
  local path=$1; shift
  nix hash path --base32 --type sha256 --sri $path
}

nix_fetch() {
  local url=$1; shift
  nix-prefetch-url $url --print-path --type sha256
}

# Try to find changes in test case directory and release new test cases ELFs when tests/ directory
# got changed.
check_before_do_release() {
  local output_file=$1; shift
  [[ -z "$output_file" ]] && echo "Missing argument 'output_file'" && return 1
  [[ -z "$GITHUB_OUTPUT" ]] && echo "Missing env 'GITHUB_OUTPUT'" && return 1

  local last_release=$(git tag --sort=committerdate | tail -n1)
  local last_release_commit=$(git rev-parse "$last_release")
  echo "Last release commit sha: $last_release_commit"
  local repo_last_commit=$(git rev-parse HEAD)
  echo "HEAD commit sha: $repo_last_commit"

  local diff_command="git diff --name-only $repo_last_commit $last_release_commit"
  [[ "$last_release_commit" = "$repo_last_commit" ]] \
    && diff_command="git diff --name-only HEAD HEAD^"

  local changed_files=$($diff_command | grep -E "^tests/")

  [[ -z "$changed_files" ]] \
    && echo "'tests/' directory unchanged, exit release workflow" \
    && echo "do_release=false" >> "$GITHUB_OUTPUT" \
    && return 0

  echo
  echo "Detect file changes in test directory between last release and HEAD"
  echo "$changed_files"
  echo

  echo "Build new tests case ELFs"
  nix build .#rvv-testcase --print-build-logs --out-link result
  tar czf "$output_file" --directory "$(realpath ./result)" .
  echo "do_release=true" >> "$GITHUB_OUTPUT"
  echo "tag=$(date +%F)+$(git rev-parse --short HEAD)" >> "$GITHUB_OUTPUT"
  echo "New testscase file pack up at $output_file"
}

bump_version() {
  local version=$1; shift

  local old_version=$(get_version)
  sed -i "s|$old_version|$version|" $NIX_FILE

  echo "Version updated to $(get_version)"
}

bump() {
  local version=$1; shift
  [[ -z "$version" ]] && echo "Missing arugment 'version'" && return 1

  bump_version "$version"

  local src_url=$(get_src_url)
  echo "Fetching $src_url"
  local new_file=$(nix_fetch "$src_url" | tail -n1)
  echo "File downloaded to $new_file"

  new_hash=$(nix_hash_file $new_file)
  echo "Generated new hash: $new_hash"
  old_hash=$(get_output_hash)
  sed -i "s|$old_hash|$new_hash|" $NIX_FILE
}
