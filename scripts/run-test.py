#!/usr/bin/env python3

from argparse import ArgumentParser
from pathlib import Path
import os
import logging
import subprocess
import json

from _utils import ColorFormatter

logger = logging.getLogger('t1-run-test')
ch = logging.StreamHandler()
ch.setFormatter(ColorFormatter())
logger.addHandler(ch)

def main():
    parser = ArgumentParser()
    parser.add_argument('case')
    parser.add_argument('-c', '--config', default="v1024l8b2-test",
                        help='configuration name, as filenames in ./configs')
    parser.add_argument('-r', '--run-config', default="debug",
                        help='run configuration name, as filenames in ./run')
    parser.add_argument('--cases-dir', help='path to testcases, default to TEST_CASES_DIR environment')
    parser.add_argument('--out-dir', default=None, help='path to save wave file and perf result file')
    parser.add_argument('-v', '--verbose', action='store_true', help='set loglevel to debug')
    parser.add_argument('-q', '--no-log', action='store_true', help='do not produce emulator log')
    args = parser.parse_args()

    if args.verbose:
        logger.setLevel(logging.DEBUG)
    else:
        logger.setLevel(logging.INFO)

    if args.cases_dir is None:
        if env_case_dir := os.environ.get('TEST_CASES_DIR'):
            args.cases_dir = env_case_dir
        else:
            logger.fatal('no testcases directory specified with TEST_CASES_DIR environment or --cases-dir argument')

    if args.out_dir is None:
        args.out_dir = f'./out/{args.config}/{args.case}/{args.run_config}'
        Path(args.out_dir).mkdir(exist_ok=True, parents=True)

    try:
        run(args)
    except subprocess.CalledProcessError as e:
        logger.error(f'failed to run "{e.args}" (return code {e.returncode}):\n{e.stderr.decode()}')
        raise e

def run(args):
    cases_dir = Path(args.cases_dir)
    case_name = args.case

    run_config_path = Path('run') / f'{args.run_config}.json'
    assert run_config_path.exists(), f'cannot find run config in {run_config_path}'
    run_config = json.loads(run_config_path.read_text())

    case_config_path = cases_dir / 'configs' / f'{case_name}.json'
    assert case_config_path.exists(), f'cannot find case config in {case_config_path}'
    config = json.loads(case_config_path.read_text())

    case_elf_path = cases_dir / config['elf']['path']
    assert case_elf_path.exists(), f'cannot find case elf in {case_elf_path}'

    elaborate_config_path = Path('configs') / f'{args.config}.json'
    assert elaborate_config_path.exists(), f'cannot find elaborate config in {elaborate_config_path}'

    process_args = ['nix', 'run', f'.#t1.{args.config}.verilator-emulator']
    env = {
        'COSIM_bin': str(case_elf_path),
        'COSIM_wave': str(Path(args.out_dir) / 'wave.fst'),
        'COSIM_timeout': str(run_config['timeout']),
        'COSIM_config': str(elaborate_config_path),
        'PERF_output_file': str(Path(args.out_dir) / 'perf.txt'),
        'EMULATOR_LOG_PATH': str(Path(args.out_dir) / 'emulator.log'),
        'EMULATOR_NO_LOG': 'true' if args.no_log else 'false',
    }
    env_repr = '\n'.join(f'{k}={v}' for k, v in env.items())
    logger.info(f'Run {" ".join(process_args)} with:\n{env_repr}')
    subprocess.Popen(process_args, env=os.environ | env).wait()
    logger.info(f'Emulator result saved in {args.out_dir}')

if __name__ == '__main__':
    main()

