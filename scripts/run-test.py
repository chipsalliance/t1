#!/usr/bin/env python3

from argparse import ArgumentParser
from pathlib import Path
import os
import logging
import subprocess
import json

from _utils import ColorFormatter

logger = logging.getLogger("t1-run-test")
ch = logging.StreamHandler()
ch.setFormatter(ColorFormatter())
logger.addHandler(ch)


def main():
    parser = ArgumentParser()
    subparsers = parser.add_subparsers(help="sub-commands help")

    # Set verilator emulator arg handler
    verilator_args_parser = subparsers.add_parser(
        "verilate", help="Run verilator emulator"
    )
    verilator_args_parser.add_argument("case", help="name alias for loading test case")
    verilator_args_parser.add_argument(
        "-c",
        "--config",
        default="v1024-l8-b2",
        help="configuration name, as filenames in ./configs",
    )
    verilator_args_parser.add_argument(
        "--trace", action="store_true", help="use emulator with trace support"
    )
    verilator_args_parser.add_argument(
        "-r",
        "--run-config",
        default="debug",
        help="run configuration name, as filenames in ./run",
    )
    verilator_args_parser.add_argument(
        "-v", "--verbose", action="store_true", help="set loglevel to debug"
    )
    verilator_args_parser.add_argument(
        "--no-log",
        action="store_true",
        help="prevent emulator produce log (both console and file)",
    )
    verilator_args_parser.add_argument(
        "-q",
        "--no-console-log",
        action="store_true",
        help="prevent emulator print log to console",
    )

    verilator_args_parser.add_argument(
        "--cases-dir", help="path to testcases, default to TEST_CASES_DIR environment"
    )
    verilator_args_parser.add_argument(
        "--out-dir", default=None, help="path to save wave file and perf result file"
    )
    verilator_args_parser.add_argument(
        "--base-out-dir",
        default=None,
        help="save result files in {base_out_dir}/{config}/{case}/{run_config}",
    )
    verilator_args_parser.add_argument(
        "--emulator-path", default=None, help="path to emulator"
    )
    # Set verilator emulator args handler
    verilator_args_parser.set_defaults(func=run_verilator_emulator)

    # Set soc runner arg handler
    soc_args_parser = subparsers.add_parser("soc", help="Run soc emulator")
    soc_args_parser.add_argument("case", help="Case name alias or a path to ELF file")
    soc_args_parser.add_argument(
        "-c",
        "--config",
        default="v1024-l8-b2",
        help="config name, as filename in ./configs. default to v1024-l8-b2",
    )
    soc_args_parser.add_argument(
        "--output-dir",
        default=None,
        help="path to save results, default to ./testrun/soc-emulator/<config>/<elf_basename>/",
    )
    soc_args_parser.add_argument(
        "--trace", action="store_true", help="enable trace file dumping"
    )
    soc_args_parser.add_argument(
        "--trace-output-file",
        default="None",
        help="path for storing trace file, default to <output-dir>/trace.fst",
    )
    soc_args_parser.add_argument(
        "--emulator-path",
        default=None,
        help="path to the soc emulator, default using nix generated one",
    )
    soc_args_parser.add_argument(
        "--cases-dir", help="path to testcases, default to TEST_CASES_DIR environment"
    )

    # Set soc args handler
    soc_args_parser.set_defaults(func=run_soc)

    # Run
    args = parser.parse_args()
    args.func(args)


def run_verilator_emulator(args):
    if args.verbose:
        logger.setLevel(logging.DEBUG)
    else:
        logger.setLevel(logging.INFO)

    if args.out_dir is None:
        if args.base_out_dir is not None:
            args.out_dir = (
                f"{args.base_out_dir}/{args.config}/{args.case}/{args.run_config}"
            )
        else:
            args.out_dir = f"./testrun/{args.config}/{args.case}/{args.run_config}"
        Path(args.out_dir).mkdir(exist_ok=True, parents=True)

    execute_verilator_emulator(args)


# Try to search ELF from the given directory
def load_elf_from_dir(cases_dir, case_name):
    if cases_dir is None:
        if env_case_dir := os.environ.get("TEST_CASES_DIR"):
            cases_dir = env_case_dir
        else:
            cases_dir = (
                subprocess.check_output(
                    "nix build .#t1.rvv-testcases.all --max-jobs 16 --no-link --print-out-paths".split()
                )
                .strip()
                .decode("UTF-8")
            )

    cases_dir = Path(cases_dir)

    case_config_path = cases_dir / "configs" / f"{case_name}.json"
    assert case_config_path.exists(), f"cannot find case config in {case_config_path}"
    config = json.loads(case_config_path.read_text())

    case_elf_path = cases_dir / config["elf"]["path"]
    assert case_elf_path.exists(), f"cannot find case elf in {case_elf_path}"

    return case_elf_path


def execute_verilator_emulator(args):
    run_config_path = Path("run") / f"{args.run_config}.json"
    assert run_config_path.exists(), f"cannot find run config in {run_config_path}"
    run_config = json.loads(run_config_path.read_text())

    case_elf_path = (
        args.case
        if Path(args.case).exists()
        else load_elf_from_dir(args.cases_dir, args.case)
    )

    elaborate_config_path = Path("configs") / f"{args.config}.json"
    assert (
        elaborate_config_path.exists()
    ), f"cannot find elaborate config in {elaborate_config_path}"

    target_name = "ip.emu-trace" if args.trace else "ip.emu"
    process_args = (
        [args.emulator_path]
        if args.emulator_path
        else ["nix", "run", f".#t1.{args.config}.{target_name}"]
    )
    env = {
        "COSIM_bin": str(case_elf_path),
        "COSIM_wave": str(Path(args.out_dir) / "wave.fst"),
        "COSIM_timeout": str(run_config["timeout"]),
        "COSIM_config": str(elaborate_config_path),
        "PERF_output_file": str(Path(args.out_dir) / "perf.txt"),
        "EMULATOR_log_path": str(Path(args.out_dir) / "emulator.log"),
        "EMULATOR_no_log": "true" if args.no_log else "false",
        "EMULATOR_no_console_log": "true" if args.no_console_log else "false",
    }
    env_repr = "\n".join(f"{k}={v}" for k, v in env.items())
    logger.info(f'Run "{" ".join(process_args)}" with:\n{env_repr}')
    return_code = subprocess.Popen(process_args, env=os.environ | env).wait()
    if return_code != 0:
        logger.error(f"Emulator exited with return code {return_code}")
        exit(return_code)
    logger.info(f"Emulator logs were saved in {args.out_dir}")


def run_soc(args):
    assert Path(
        f"./configs/{args.config}.json"
    ).exists(), f"./configs/{args.config}.json doesn't exists. \nHint: are you running this script in project root?"

    target_name = "subsystem.emu-trace" if args.trace else "subsystem.emu"
    process_args = (
        [args.emulator_path]
        if args.emulator_path
        else ["nix", "run", f".#t1.{args.config}.{target_name}", "--"]
    )

    elf_path = (
        args.case
        if Path(args.case).exists()
        else load_elf_from_dir(args.cases_dir, args.case)
    )
    process_args.append(f"+init_file={elf_path}")

    elf_filename = os.path.splitext(os.path.basename(elf_path))[0]
    if args.output_dir is None:
        args.output_dir = f"./testrun/soc-emulator/{args.config}/{elf_filename}/"
        logger.info(f"Output dir set to {args.output_dir}")

    trace_filepath = args.trace_output_file or f"{args.trace_output_dir}/trace.fst"
    process_args.append(
        f"+trace_file={trace_filepath}"
        if args.trace
        else ""  # return empty string if trace is not enable
    )

    logger.info(f"Running {' '.join(process_args)}")
    return_code = subprocess.Popen(process_args).wait()
    if return_code != 0:
        logger.error(f"Emulator exited with return code {return_code}")
        exit(return_code)

    logger.info("soc emulator exit with success")
    if args.trace:
        logger.info(f"Trace store in {trace_filepath}")


if __name__ == "__main__":
    main()
