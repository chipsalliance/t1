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
    parser.add_argument("case")
    parser.add_argument(
        "-c",
        "--config",
        default="v1024-l8-b2",
        help="configuration name, as filenames in ./configs",
    )
    parser.add_argument(
        "--trace", action="store_true", help="use emulator with trace support"
    )
    parser.add_argument(
        "-r",
        "--run-config",
        default="debug",
        help="run configuration name, as filenames in ./run",
    )
    parser.add_argument(
        "-v", "--verbose", action="store_true", help="set loglevel to debug"
    )
    parser.add_argument(
        "--no-log",
        action="store_true",
        help="prevent emulator produce log (both console and file)",
    )
    parser.add_argument(
        "-q",
        "--no-console-log",
        action="store_true",
        help="prevent emulator print log to console",
    )
    parser.add_argument(
        "-s",
        "--soc",
        action="store_true",
        default=None,
        help="simulate with SoC framework",
    )

    parser.add_argument(
        "--cases-dir", help="path to testcases, default to TEST_CASES_DIR environment"
    )
    parser.add_argument(
        "--out-dir", default=None, help="path to save wave file and perf result file"
    )
    parser.add_argument(
        "--base-out-dir",
        default=None,
        help="save result files in {base_out_dir}/{config}/{case}/{run_config}",
    )
    parser.add_argument("--emulator-path", default=None, help="path to emulator")
    args = parser.parse_args()

    if args.verbose:
        logger.setLevel(logging.DEBUG)
    else:
        logger.setLevel(logging.INFO)

    if args.cases_dir is None:
        if env_case_dir := os.environ.get("TEST_CASES_DIR"):
            args.cases_dir = env_case_dir
        else:
            logger.fatal(
                "no testcases directory specified with TEST_CASES_DIR environment or --cases-dir argument"
            )
            exit(1)

    if args.out_dir is None:
        if args.base_out_dir is not None:
            args.out_dir = (
                f"{args.base_out_dir}/{args.config}/{args.case}/{args.run_config}"
            )
        else:
            args.out_dir = f"./testrun/{args.config}/{args.case}/{args.run_config}"
        Path(args.out_dir).mkdir(exist_ok=True, parents=True)

    run(args)


def run(args):
    cases_dir = Path(args.cases_dir)
    case_name = args.case

    run_config_path = Path("run") / f"{args.run_config}.json"
    assert run_config_path.exists(), f"cannot find run config in {run_config_path}"
    run_config = json.loads(run_config_path.read_text())

    case_config_path = cases_dir / "configs" / f"{case_name}.json"
    assert case_config_path.exists(), f"cannot find case config in {case_config_path}"
    config = json.loads(case_config_path.read_text())

    case_elf_path = cases_dir / config["elf"]["path"]
    assert case_elf_path.exists(), f"cannot find case elf in {case_elf_path}"

    elaborate_config_path = Path("configs") / f"{args.config}.json"
    assert (
        elaborate_config_path.exists()
    ), f"cannot find elaborate config in {elaborate_config_path}"

    target_name = "verilator-emulator-trace" if args.trace else "verilator-emulator"
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


if __name__ == "__main__":
    main()
