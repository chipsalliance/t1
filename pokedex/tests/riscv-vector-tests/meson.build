fs = import('fs')

case_list = fs.read('case_list.txt').strip().split('\n')
if get_option('zve32f').enabled()
  case_list += fs.read('fp_case_list.txt').strip().split('\n')
endif

if not has_prebuilt_dir
  cflags = base_cflags + [
    '-I' + stub_include,
    '-I' + riscv_tests_src / 'isa/macros/scalar',
  ]

  codegen_flags = [
    '-VLEN',
    get_option('vlen').to_string(),
    '-XLEN',
    get_option('xlen').to_string(),
    '-float16=false',
    '-repeat=16',
    '-testfloat3level=2',
  ]
  single = find_program('single', dirs: [codegen_install_dir / 'bin'])
endif

targets = []
cases = []

suite = 'riscv-vector-tests'

foreach line : case_list
  case = line.strip()
  if case == '' or case.startswith('#')
    continue
  endif

  # underscorify() replace non-alphabetical non-number character to '_'
  case_name = case.underscorify()

  # --- Populate Targets ---
  if with_tests and has_prebuilt_dir
    elf = prebuilt_dir / suite / 'bin' / case_name + '.elf'
  else
    codegen_config = codegen_install_dir / 'configs/v' / case + '.toml'

    # Generate .S file
    src = custom_target(
      suite + '.' + case_name + '_src',
      input: [codegen_config],
      output: case_name + '.S',
      command: [single] + codegen_flags + [
        '-configfile',
        '@INPUT@',
        '-outputfile',
        '@OUTPUT@',
      ],
    )

    # Build ELF
    elf = custom_target(
      suite + '.' + case_name,
      input: [src],
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
      install_dir: suite / 'dump',
    )

    targets += [elf, dump]
  endif

  cases += [[case_name, elf]]
endforeach

# Creat a phony target for user to build individual tests
if not has_prebuilt_dir
  alias_target('riscv_vector_tests', targets)
endif

if with_tests
  global_test_suites += {suite: cases}
endif
