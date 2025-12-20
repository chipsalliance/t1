project('pokedex-tests-suite', version: '1.0', meson_version: '>1.4.1')

with_tests = get_option('with_tests').enabled()
prebuilt_dir = get_option('prebuilt_case_dir')
has_prebuilt_dir = prebuilt_dir != ''
march = get_option('march')

# --- 1. Setup & Configuration ---
if not has_prebuilt_dir
  clang = find_program('riscv32-none-elf-clang')
  objdump = find_program('riscv32-none-elf-objdump')
endif

if with_tests
  # Nix timestamps are fixing up everything looks like it's from 1970, which
  # effectively breaks Ninja's dependency logic. I want tests in the build DAG.
  # Since the mtimes are useless, rsync checksums could help forcing an update
  # on the simulator binaries only when the content actually changes. It's a
  # hack to make the build system realize the inputs are new so the tests
  # re-run.
  rsync = find_program('rsync')
  _sims = ['spike', 'pokedex']

  _cached_sims = []
  foreach program : _sims
    _cached_sims += custom_target(
      'cached_' + program,
      input: find_program(program).full_path(),
      output: program,
      command: [rsync, '--checksum', '--no-times', '@INPUT@', '@OUTPUT@'],
      build_always_stale: true,
    )
  endforeach

  spike = _cached_sims[0]
  pokedex = _cached_sims[1]

  difftest_runner = find_program('difftest.py')
  pokedex_config = meson.current_source_dir() / 'pokedex-config.kdl'
endif

# --- 2. Resources & Share Vars ---
stub_main = meson.current_source_dir() / 'compile-stubs/main.S'
stub_linker_script = meson.current_source_dir() / 'compile-stubs/script.ld'
stub_include = meson.current_source_dir() / 'compile-stubs/include'

if not has_prebuilt_dir
  codegen_install_dir = get_option('codegen_install_dir')
  if not codegen_install_dir.startswith('/')
    error('-Dcodegen_intall_dir is not supply or invalid')
  endif
  riscv_tests_src = get_option('riscv_tests_src')
  if not riscv_tests_src.startswith('/')
    error('-Driscv_tests_src is not supply or invalid')
  endif
endif

# --- 3. Common flags ---
base_cflags = [
  '-march=' + get_option('march'),
  '-mabi=' + get_option('abi'),
  '-T' + stub_linker_script,
  '-static',
  '-mcmodel=medany',
  '-nostdlib',
]

objdump_flags = [
  '--disassemble-all',
  '--disassemble-zeroes',
  '--section=.text',
  '--section=.text.startup',
  '--section=.text.init',
  '--section=.data',
]

# Each subdir should update this set if they want to run the test.
#
# Suite spec layout:
# global_test_suites = {
#   'riscv-tests': [
#      ['rv32ui_addi', Path(rv32ui_addi.elf)]
#      ['rv32ui_muli', Path(rv32ui_muli.elf)]
#   ]
# }
global_test_suites = {}

# --- 4. Include ---
subdir('smoke')
subdir('smoke_v')
subdir('riscv-vector-tests')
subdir('riscv-tests')

# --- 5. Tests ---
if with_tests
  foreach suite_name, suite_cases : global_test_suites
    foreach case_spec : suite_cases
      case_name = case_spec[0]
      elf = case_spec[1]

      diff_result = custom_target(
        suite_name + '.' + case_name + '_diff_result',
        depends: [spike, pokedex],
        input: elf,
        output: [
          suite_name + '.' + case_name + '_spike_commit.log',
          suite_name + '.' + case_name + '_pokedex_commit.jsonl',
          suite_name + '.' + case_name + '_diff_result.json',
        ],
        env: [
          'MARCH=' + march,
          'SPIKE=' + spike.full_path(),
          'POKEDEX=' + pokedex.full_path(),
          'POKEDEX_CONFIG=' + pokedex_config,
        ],
        command: [
          difftest_runner,
          '--elf',
          '@INPUT@',
          '--spike-log',
          '@OUTPUT0@',
          '--pokedex-log',
          '@OUTPUT1@',
          '--diff-result',
          '@OUTPUT2@',
        ],
        install: true,
        install_dir: suite_name / case_name,
        build_by_default: false,  # Never build until explicit
      )

      test(
        case_name,
        difftest_runner,
        args: ['--check', '--diff-result', diff_result[2]],
        depends: [diff_result],
        suite: suite_name,
      )
    endforeach
  endforeach
endif
