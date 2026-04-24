# SPDX-License-Identifier: Apache-2.0
# SPDX-FileCopyrightText: 2026 Jiuyang Liu <liu@jiuyang.me>
#
# Formality LEC script for T1 Chisel-to-zaozi migration equivalence checking
# Adapted from ~/dwbb-zaozi/lec/scripts/dwbb_fm.tcl
#
# Usage: fm_shell -f t1_fm.tcl \
#          -ref_file "path/to/ref/Module.sv" \
#          -impl_file "path/to/impl/Module.sv" \
#          -ref_module "Module" \
#          -impl_module "Module" \
#          -report_dir "./reports"
#
# All migrated modules use the same module name on both ref and impl sides.

set start_time [clock seconds]
echo [clock format ${start_time} -gmt false]

# Variables passed from command line
if { ![info exists ref_file] }      { puts "ERROR: ref_file not set"; exit 1 }
if { ![info exists impl_file] }     { puts "ERROR: impl_file not set"; exit 1 }
if { ![info exists ref_module] }    { puts "ERROR: ref_module not set"; exit 1 }
if { ![info exists impl_module] }   { puts "ERROR: impl_module not set"; exit 1 }
if { ![info exists report_dir] }    { set report_dir "./reports" }

set module_name "${ref_module}_vs_${impl_module}"

file mkdir $report_dir

# Formality settings (adapted from dwbb-zaozi)
set verification_assume_reg_init none
set_app_var verification_clock_gate_edge_analysis true
set_app_var verification_inversion_push true
set_app_var hdlin_dwroot ""
set_app_var verification_set_undriven_signals "BINARY:X"
set_app_var verification_verify_directly_undriven_output true

# Mismatch filters (evaluate which apply to T1's Verilog)
set_mismatch_message_filter -warn FMR_ELAB-034
set_mismatch_message_filter -warn FMR_ELAB-058
set_mismatch_message_filter -warn FMR_ELAB-147

# Read reference design (pre-migration Chisel-generated Verilog)
read_sverilog -r -work_library WORK $ref_file
set_top r:/WORK/${ref_module}

# Read implementation design (post-migration zaozi-generated Verilog)
read_sverilog -i -work_library WORK $impl_file
set_top i:/WORK/${impl_module}

# Match and verify
match

report_matched_points   > ${report_dir}/${module_name}.matched.fm
report_unmatched_points > ${report_dir}/${module_name}.unmatched.fm

set status [ verify ]

report_passing_points   > ${report_dir}/${module_name}.passed.fm
report_failing_points   > ${report_dir}/${module_name}.failed.fm
report_aborted_points   > ${report_dir}/${module_name}.aborted.fm

if {$status == 0} {
  analyze_points -all   > ${report_dir}/${module_name}.analysis.fm
  save_session -replace   ${report_dir}/${module_name}.fss
}

report_status

set end_time [clock seconds]
echo "Time elapsed: [expr $end_time - $start_time] seconds"

if {$status == 1} {
  echo "LEC PASSED: ${module_name}"
  exit 0
} else {
  echo "LEC FAILED: ${module_name}"
  exit 1
}
