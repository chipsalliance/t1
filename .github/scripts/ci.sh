NIX_FILE="./nix/rvv-testcase-unwrapped.nix"
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

# Try to build testcase and compare it's hash to the previous release
check_before_do_release() {
  local output_file=$1; shift

  nix build .#testcase --print-build-logs --out-link result
  local output_dir=$(realpath ./result)
  local new_hash=$(nix_hash_path $output_dir)

  # Nix hash for tar archive changed everytime when testcase got rebuild in GitHub Action.
  # So here I have to use nix hash path to compare only testcase elf and configs.
  local original_file=$(nix_fetch $(get_src_url) | tail -n1)
  local temp_dir=$(mktemp -d -t "rvv-testcase-XXX")
  tar xzf "$original_file" -C "$temp_dir"
  local old_hash=$(nix_hash_path $temp_dir)

  if [[ "$old_hash" = "$new_hash" ]]; then
    echo "Hash are still the same, no release will be made"
    echo "do_release=false" >> $GITHUB_OUTPUT
  else
    echo "Hash is changed from $old_hash to $new_hash, make new release"
    echo "Different content: "
    diff -bur $temp_dir $output_dir

    echo "do_release=true" >> $GITHUB_OUTPUT
    echo "tag=$(date +%F)+$(git rev-parse --short HEAD)" >> $GITHUB_OUTPUT
    tar czf $output_file --directory $output_dir .
  fi
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
