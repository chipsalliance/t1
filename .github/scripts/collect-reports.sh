#!/usr/bin/env bash

set -e

allTops=("t1emu" "t1rocketemu")
for t in "${allTops[@]}"; do
  nix run ".#ci-helper" -- postCI \
    --urg-report-file-path ./urg-report.md \
    --cycle-update-file-path ./cycle-update.md \
    --emu-lib 'vcs' \
    --top "$t"
  echo "# $t" >> $GITHUB_STEP_SUMMARY
  cat ./urg-report.md >> $GITHUB_STEP_SUMMARY
  echo >> $GITHUB_STEP_SUMMARY
  cat ./cycle-update.md >> $GITHUB_STEP_SUMMARY
done

for t in "${allTops[@]}"; do
  nix run ".#ci-helper" -- postCI \
    --cycle-update-file-path ./cycle-update.md \
    --emu-lib "verilator" \
    --case-dir "verilator" \
    --top "$t"
  echo >> $GITHUB_STEP_SUMMARY
  cat ./cycle-update.md >> $GITHUB_STEP_SUMMARY
done
