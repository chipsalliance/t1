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

log() {
  echo "[bump-prebuilt-testcases]" "$@" >&2
}

# Check if files in ./tests get changed
# If changed, echo do_release=true tag=<release-tag> to $GITHUB_OUTPUT, and put testcases tarball into $output_file
build_testcases_if_updated() {
  local output_file="$1"; shift
  [[ -z "$output_file" ]] && echo "Missing argument 'output_file'" && return 1
  [[ -z "$GITHUB_OUTPUT" ]] && echo "Missing env 'GITHUB_OUTPUT'" && return 1

  local last_release=$(git tag --sort=committerdate | tail -n1)
  local last_release_commit=$(git rev-parse "$last_release")
  log "Last release commit sha: $last_release_commit"
  local repo_last_commit=$(git rev-parse HEAD)
  log "HEAD commit sha: $repo_last_commit"

  local diff_command="git diff --name-only $repo_last_commit $last_release_commit"
  [[ "$last_release_commit" = "$repo_last_commit" ]] \
    && diff_command="git diff --name-only HEAD HEAD^"

  local changed_files=$($diff_command | grep -E "^tests/")

  [[ -z "$changed_files" ]] \
    && log "'tests/' directory unchanged, exit release workflow" \
    && log "do_release=false" >> "$GITHUB_OUTPUT" \
    && return 0

  log "Detected the following files changes is ./tests between last release and HEAD"
  while read -r f; do
    log "- $f"
  done <<< "$changed_files"

  log "Build new tests case derivation"
  result=$(nix build .#t1.rvv-testcases.out --print-build-logs --no-link --print-out-paths)
  tar czf "$output_file" --directory "$result" .
  echo "do_release=true" >> "$GITHUB_OUTPUT"
  echo "tag=$(date +%F)+$(git rev-parse --short HEAD)" >> "$GITHUB_OUTPUT"
  log "New testscase tarball at $output_file"
}

bump_version() {
  local version="$1"
  local nix_file="$2"

  local old_version=$(get_version)
  sed -i "s|$old_version|$version|" "$nix_file"

  echo "Version updated to $(get_version)"
}

bump() {
  local version="$1"
  local nix_file="$2"
  [[ -z "$version" ]] && echo "Missing arugment 'version'" && return 1

  bump_version "$version"

  local src_url=$(get_src_url)
  echo "Fetching $src_url"
  local new_file=$(nix_fetch "$src_url" | tail -n1)
  echo "File downloaded to $new_file"

  new_hash=$(nix_hash_file $new_file)
  echo "Generated new hash: $new_hash"
  old_hash=$(get_output_hash)
  sed -i "s|$old_hash|$new_hash|" "$nix_file"
}

action="$1"
shift
case "$action" in
  bump)
    bump "$@"
    ;;
  build_testcases_if_updated)
    build_testcases_if_updated "$@"
    ;;
  *)
    log "unknown action '$action'"
    exit 1
esac

