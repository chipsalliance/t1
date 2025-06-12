#!/usr/bin/env bash

set -o pipefail
set -o errexit

PATH="$(nix build '.#gawk.out' --print-out-paths --no-link)/bin:$PATH"
PATH="$(nix build '.#jq.out' --print-out-paths --no-link)/bin:$PATH"

passWith() {
  local msg="$1"; shift
  echo
  printf ">> PASS: $msg\n" "$@"
  echo
}

failWith() {
  local msg="$1"; shift
  echo
  printf ">> FAIL: $msg\n" "$@" >&2
  echo
  exit 1
}

T1_CONFIG=${T1_CONFIG:-}
T1_TOP=${T1_TOP:-}
T1_EMU=${T1_EMU:-}

nix build ".#t1.${T1_CONFIG}.${T1_TOP}.run.emurt-test.simple.${T1_EMU}" --impure

# Test 1: Test mmio-event.jsonl file exists
mmioResultFile="$(realpath ./result/mmio-event.jsonl)"
if [[ ! -r "$mmioResultFile" ]]; then
  failWith "mmio-event.jsonl not found"
fi
passWith "mmio-result file found ($mmioResultFile)"

# Test 2: Test output is expected String
mmioOutput=$(jq -r 'select(.event == "uart-write") | .value' "$mmioResultFile" \
  | awk '{ printf "%c", $1}')
expectResult="Hello, World"
if [[ "$mmioOutput" != "$expectResult" ]]; then
  failWith "Expect '$expectResult', got '$mmioOutput'"
fi
passWith "mmio-result is expected ($mmioOutput)"

# Test 3: Test program instrument did work
calculator=$(realpath ./nix/t1/run/calculate-cycle.py)
cd $(dirname "$mmioResultFile")
cycles=$(python3 "$calculator")
total_cycles=$(jq .total_cycles sim_result.json)
if (( $cycles < 0 || $cycles > $total_cycles )); then
  failWith "invalid program instrument $cycles found"
fi
passWith "program instrument cycles is valid ($cycles)"
