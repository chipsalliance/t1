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

nix_hash() {
  local filepath=$1; shift
  nix hash file --base16 --type sha256 --sri $filepath
}

nix_fetch() {
  local url=$1; shift
  nix-prefetch-url $url --print-path --type sha256
}

check_before_do_release() {
  local artifact=$1; shift
  local new_hash=$(nix_hash $artifact)
  local old_hash=$(get_output_hash)
  if [[ "$old_hash" = "$new_hash" ]]; then
    echo "Hash are still the same, no release will be made"
    echo "do_release=false" >> $GITHUB_OUTPUT
  else
    echo "Hash is changed from $old_hash to $new_hash, make new release"
    echo "do_release=true" >> $GITHUB_OUTPUT
  fi
}

bump() {
  local src_url=$(get_src_url)
  echo "Fetching $src_url"
  local new_file=$(nix_fetch "$src_url" | tail -n1)
  echo "File downloaded to $new_file"
  new_hash=$(nix_hash $new_file)
  echo "Generated new hash: $new_hash"
  old_hash=$(get_output_hash)
  sed -i "s|$old_hash|$new_hash|" $NIX_FILE
}
