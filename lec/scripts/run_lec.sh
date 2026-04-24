#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# SPDX-FileCopyrightText: 2026 Jiuyang Liu <liu@jiuyang.me>
#
# Run Formality LEC for a T1 module comparing pre-migration (ref) vs post-migration (impl) Verilog.
# Adapted from ~/dwbb-zaozi/nix/dwbb/lec-run.nix pattern.
#
# Usage: run_lec.sh <ref-rtl-dir> <impl-rtl-dir> <ref-module> <impl-module> [report-dir]
#
# For Phase 1 modules (direct ExtModule): ref-module == impl-module
# After the public-boundary refactor, Phase 2 uses the same public module name
# on both sides as well.
#
# Example:
#   # Phase 1 (same name):
#   ./lec/scripts/run_lec.sh ref-rtl impl-rtl LanePopCount LanePopCount
#   # Phase 2:
#   ./lec/scripts/run_lec.sh ref-rtl impl-rtl LaneShifter LaneShifter

set -euo pipefail

if [ $# -lt 4 ]; then
  echo "Usage: $0 <ref-rtl-dir> <impl-rtl-dir> <ref-module> <impl-module> [report-dir]"
  echo ""
  echo "Arguments:"
  echo "  ref-rtl-dir   Path to reference RTL directory (pre-migration)"
  echo "  impl-rtl-dir  Path to implementation RTL directory (post-migration)"
  echo "  ref-module    Reference module name (e.g., LaneShifter)"
  echo "  impl-module   Implementation module name (e.g., LaneShifter)"
  echo "  report-dir    Optional: directory for Formality reports (default: ./lec-reports/<ref-module>)"
  exit 1
fi

REF_DIR="$1"
IMPL_DIR="$2"
REF_MODULE="$3"
IMPL_MODULE="$4"
REPORT_DIR="${LEC_REPORT_DIR:-${5:-./lec-reports/${REF_MODULE}}}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TCL_SCRIPT="$SCRIPT_DIR/t1_fm.tcl"

REF_FILE="$REF_DIR/$REF_MODULE.sv"
IMPL_FILE="$IMPL_DIR/$IMPL_MODULE.sv"

# Validate inputs
if [ ! -f "$REF_FILE" ]; then
  echo "ERROR: Reference file not found: $REF_FILE"
  echo "  Available .sv files in ref dir:"
  ls "$REF_DIR"/*.sv 2>/dev/null | head -10 || echo "  (none)"
  exit 1
fi

if [ ! -f "$IMPL_FILE" ]; then
  echo "ERROR: Implementation file not found: $IMPL_FILE"
  echo "  Available .sv files in impl dir:"
  ls "$IMPL_DIR"/*.sv 2>/dev/null | head -10 || echo "  (none)"
  exit 1
fi

if [ ! -f "$TCL_SCRIPT" ]; then
  echo "ERROR: TCL script not found: $TCL_SCRIPT"
  exit 1
fi

mkdir -p "$REPORT_DIR"

echo "=== T1 LEC: $REF_MODULE vs $IMPL_MODULE ==="
echo "  Reference:      $REF_FILE (module $REF_MODULE)"
echo "  Implementation: $IMPL_FILE (module $IMPL_MODULE)"
echo "  Reports:        $REPORT_DIR"
echo ""

# Run Formality inside snps-fhs-env (FM_HOME set by snps-fhs-env profile)
# fm_shell does not accept custom flags; pass variables via -x and source the script
# Use || true to capture output even on failure (fm_shell exits non-zero on LEC fail)
RESULT=$(snps-fhs-env -c "\
  \$FM_HOME/bin/fm_shell \
    -r \$FM_HOME \
    -x 'set ref_file $REF_FILE; \
        set impl_file $IMPL_FILE; \
        set ref_module $REF_MODULE; \
        set impl_module $IMPL_MODULE; \
        set report_dir $REPORT_DIR; \
        source $TCL_SCRIPT' \
" 2>&1) || true

if echo "$RESULT" | grep -q "LEC PASSED"; then
  echo ""
  echo "LEC RESULT: PASS"
  echo "$RESULT" | grep -E "LEC PASSED|Verification Results|Time elapsed" || true
  echo "Reports saved to: $REPORT_DIR"
  exit 0
else
  echo ""
  echo "LEC RESULT: FAIL"
  echo "$RESULT" | tail -30
  echo "Reports saved to: $REPORT_DIR"
  exit 1
fi
