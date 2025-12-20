fs = import('fs')

cflags = base_cflags

srcs = {'addi': 'src/addi.c', 'mul': 'src/mul.S'}

cases = []
targets = []

suite = 'smoke'

foreach case_name, case_src : srcs
  # --- Populate Targets ---
  if with_tests and has_prebuilt_dir
    elf = prebuilt_dir / suite / 'bin' / case_name + '.elf'
  else
    elf = custom_target(
      suite + '.' + case_name,
      input: [case_src, stub_main],
      output: case_name + '.elf',
      command: [clang] + cflags + ['@INPUT@', '-o', '@OUTPUT@'],
      build_by_default: true,
      install: true,
      install_dir: suite / 'bin',
    )

    dump = custom_target(
      suite + '.' + case_name + '_dump',
      input: elf,
      output: case_name + '.objdump',
      command: [objdump] + objdump_flags + ['@INPUT@'],
      capture: true,
      build_by_default: true,
      install: true,
      install_dir: suite / 'dumps',
    )

    targets += [elf, dump]
  endif

  cases += [[case_name, elf]]
endforeach

# Creat a phony target for user to build individual tests
if not has_prebuilt_dir
  alias_target('smoke', targets)
endif

if with_tests
  global_test_suites += {suite: cases}
endif
