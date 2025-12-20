fs = import('fs')

case_list = fs.read('case_list.txt').strip().split('\n')
if get_option('scalar_fp').enabled()
  case_list += fs.read('fp_case_list.txt').strip().split('\n')
endif

if not has_prebuilt_dir
  cflags = base_cflags + [
    '-I' + stub_include,
    '-I' + riscv_tests_src / 'isa/macros/scalar',
  ]
else
  cflags = []
endif

targets = []
cases = []

suite = 'riscv-tests'

foreach line : case_list
  case_src = line.strip()
  if case_src == '' or case_src.startswith('#')
    continue
  endif

  # underscorify() replace non-alphabetical non-number character to '_'
  case_name = case_src.replace('.S', '').underscorify()

  if with_tests and prebuilt_dir != ''
    # Use system ELF
    elf = prebuilt_dir / suite / 'bin' / case_name + '.elf'
  else
    src_file = riscv_tests_src / 'isa' / case_src

    # Build ELF
    elf = custom_target(
      suite + '.' + case_name,
      input: [src_file],
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
  alias_target('riscv_tests', targets)
endif

if with_tests
  global_test_suites += {suite: cases}
endif
