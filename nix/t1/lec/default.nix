# SPDX-License-Identifier: Apache-2.0
# SPDX-FileCopyrightText: 2026 Jiuyang Liu <liu@jiuyang.me>
#
# LEC (Logic Equivalence Checking) infrastructure for T1 zaozi migration.
#
# Layered architecture:
#   ref-verilog: pre-migration RTL (nix build)
#   impl-verilog: post-migration RTL (nix build)
#   lec-run.<module>: Formality comparison per module (nix run --impure)
#
# Usage (requires FM_HOME env var and --impure for lec-run):
#   nix build .#t1.<config>.<target>.lec.ref-verilog
#   nix build .#t1.<config>.<target>.lec.impl-verilog
#   nix run --impure .#t1.<config>.<target>.lec.lec-run.LanePopCount
#   nix run --impure .#t1.<config>.<target>.lec.lec-run.LaneFFO
#   nix run --impure .#t1.<config>.<target>.lec.lec-run.LaneShifter
#   nix run --impure .#t1.<config>.<target>.lec.lec-run.MaskedLogic
{
  lib,
  writeShellApplication,
  snps-fhs-env,
}:

{
  refRtl,
  implRtl,
  fmScript,
}:

let
  lecModuleSpecs = [
    { name = "LanePopCount"; refModule = "LanePopCount"; implModule = "LanePopCount"; }
    { name = "LaneFFO";      refModule = "LaneFFO";      implModule = "LaneFFO"; }
    { name = "LaneShifter";  refModule = "LaneShifter";  implModule = "LaneShifter"; }
    { name = "MaskedLogic";  refModule = "MaskedLogic";  implModule = "MaskedLogic"; }
  ];

  mkLecRun = spec: writeShellApplication {
    name = "lec-run-${spec.name}";
    text = ''
      REF_FILE="${refRtl}/${spec.refModule}.sv"
      IMPL_FILE="${implRtl}/${spec.implModule}.sv"
      REPORT_DIR="''${LEC_REPORT_DIR:-./lec-reports/${spec.name}}"
      mkdir -p "$REPORT_DIR"

      if [ ! -f "$REF_FILE" ]; then
        echo "ERROR: Reference file not found: $REF_FILE"
        exit 1
      fi
      if [ ! -f "$IMPL_FILE" ]; then
        echo "ERROR: Implementation file not found: $IMPL_FILE"
        exit 1
      fi

      echo "=== T1 LEC: ${spec.name} ==="
      echo "  Reference:      $REF_FILE (module ${spec.refModule})"
      echo "  Implementation: $IMPL_FILE (module ${spec.implModule})"
      echo "  Reports:        $REPORT_DIR"
      echo ""

      # Use || true to capture output even on failure (fm_shell exits non-zero on LEC fail)
      RESULT=$(${snps-fhs-env}/bin/snps-fhs-env -c "\
        \$FM_HOME/bin/fm_shell \
          -r \$FM_HOME \
          -x 'set ref_file $REF_FILE; \
              set impl_file $IMPL_FILE; \
              set ref_module ${spec.refModule}; \
              set impl_module ${spec.implModule}; \
              set report_dir $REPORT_DIR; \
              source ${fmScript}' \
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
    '';
  };
in
{
  ref-verilog = refRtl;
  impl-verilog = implRtl;
  lec-run = lib.listToAttrs (map (spec: { name = spec.name; value = mkLecRun spec; }) lecModuleSpecs);
}
