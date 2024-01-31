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
    subparsers = parser.add_subparsers(help="sub-commands help", required=True)

    # Add sub-commands
    verilator_args_parser = subparsers.add_parser("verilate", help="ip emulator help")  # TODO: rename to ip
    verilator_args_parser.set_defaults(func=run_ip)
    soc_args_parser = subparsers.add_parser("soc", help="soc emulator help")
    soc_args_parser.set_defaults(func=run_soc)

    # Register common args
    for subparser in (verilator_args_parser, soc_args_parser):
        subparser.add_argument("case", help="Case name alias or a path to ELF file")
        subparser.add_argument(
            "-c",
            "--config",
            default="v1024-l8-b2",
            help="config name, as filename in ./configs. default to v1024-l8-b2",
        )
        subparser.add_argument(
            "--trace", action="store_true", help="enable trace file dumping"
        )
        subparser.add_argument(
            "--emulator-path",
            default=None,
            help="path to the soc emulator, use nix generated one if unspecified",
        )
        subparser.add_argument(
            "--cases-dir", help="path to testcases, default to TEST_CASES_DIR environment"
        )
        subparser.add_argument(
            "--use-individual-drv", help="use .#t1.rvv-testcases.<case_type>.<case_name> instead of .#t1.rvv-testcases.all",
            action="store_true",
        )
        subparser.add_argument(
            "--out-dir",
            default=None,
            help="path to save results",  # TODO: give a consistent behavior for both verilate and soc emulator
        )
        subparser.add_argument(
            "--base-out-dir",
            default=None,
            help="save result files in {base_out_dir}/{config}/{case}",
        )

    # Register verilator emulator args
    verilator_args_parser.add_argument(
        "-d",
        "--dramsim3-cfg",
        help="Enable dramsim3, and specify its configuration file",
    )
    verilator_args_parser.add_argument(
        "-f",
        "--frequency",
        help="frequency for the vector processor (in MHz)",
        default=2000,
        type=float,
    )
    verilator_args_parser.add_argument(
        "--cosim-timeout", default=100000, help="set cosim timeout"
    )
    verilator_args_parser.add_argument(
        "-v", "--verbose", action="store_true", help="set loglevel to debug"
    )
    verilator_args_parser.add_argument(
        "--no-logging",
        action="store_true",
        help="prevent emulator produce log (both console and file)",
    )
    verilator_args_parser.add_argument(
        "--no-file-logging",
        action="store_false",
        default=True,
        help="prevent emulator write log to file",
    )
    verilator_args_parser.add_argument(
        "-q",
        "--no-console-logging",
        action="store_true",
        help="prevent emulator print log to console",
    )

    # Register soc emulator args
    soc_args_parser.add_argument(
        "--trace-out-file",
        default="None",
        help="path for storing trace file, default to <output-dir>/trace.fst",
    )

    # Run
    args = parser.parse_args()
    args.func(args)


# Try to search ELF from the given directory
def load_elf_from_dir(cases_dir, case_name, use_individual_drv=False):
    if cases_dir is None:
        if env_case_dir := os.environ.get("TEST_CASES_DIR"):
            cases_dir = env_case_dir
        else:
            if use_individual_drv:
                split_idx = case_name.rfind('-')
                case_true_name, case_type = case_name[:split_idx], case_name[split_idx+1:]
                cases_dir = (
                    subprocess.check_output(
                        f"nix build .#t1.rvv-testcases.{case_type}.{case_true_name} --max-jobs 16 --no-link --print-out-paths".split()
                    )
                    .strip()
                    .decode("UTF-8")
                )
            else:
                cases_dir = (
                    subprocess.check_output(
                        "nix build .#t1.rvv-testcases.all --max-jobs 16 --no-link --print-out-paths".split()
                    )
                    .strip()
                    .decode("UTF-8")
                )

    cases_dir = Path(cases_dir)

    case_config_path = cases_dir / f"{case_name}.json" if use_individual_drv else cases_dir / "configs" / f"{case_name}.json"
    assert case_config_path.exists(), f"cannot find case config in {case_config_path}"
    config = json.loads(case_config_path.read_text())

    case_elf_path = cases_dir / config["elf"]["path"]
    assert case_elf_path.exists(), f"cannot find case elf in {case_elf_path}"

    return case_elf_path


def run_ip(args):
    if args.verbose:
        logger.setLevel(logging.DEBUG)
    else:
        logger.setLevel(logging.INFO)

    if args.out_dir is None:
        if args.base_out_dir is not None:
            args.out_dir = f"{args.base_out_dir}/{args.config}/{args.case}"
        else:
            args.out_dir = f"./testrun/{args.config}/{args.case}"
        Path(args.out_dir).mkdir(exist_ok=True, parents=True)

    case_elf_path = (
        args.case
        if Path(args.case).exists()
        else load_elf_from_dir(args.cases_dir, args.case, args.use_individual_drv)
    )

    dramsim3_cfg = args.dramsim3_cfg

    tck = 10**3 / args.frequency

    logger.info("Running configgen")
    elaborate_config_path = Path(f"{args.out_dir}/config.json")
    if elaborate_config_path.exists():
        os.remove(elaborate_config_path)
    subprocess.check_call(
        [
            "nix",
            "run",
            ".#t1.configgen",
            "--",
            f"{args.config.replace('-', '')}",
            "-t",
            f"{args.out_dir}",
        ]
    )
    assert (
        elaborate_config_path.exists()
    ), f"cannot find elaborate config in {elaborate_config_path}"

    logger.info("Running emulator builder")
    target_name = "ip.emu-trace" if args.trace else "ip.emu"
    process_args = (
        [args.emulator_path]
        if args.emulator_path
        else ["nix", "run", f".#t1.{args.config}.{target_name}", "--"]
    ) + [
        "--elf",
        str(case_elf_path),
        "--wave",
        str(Path(args.out_dir) / "wave.fst"),
        "--timeout",
        str(args.cosim_timeout),
        "--config",
        str(elaborate_config_path),
        "--tck",
        str(tck),
        "--perf",
        str(Path(args.out_dir) / "perf.txt"),
        "--no-logging" if (args.no_logging) else None,
        "--no-file-logging" if (args.no_file_logging) else None,
        "--no-console-logging" if (args.no_console_logging) else None,
        f"--log-path={str(Path(args.out_dir) / 'emulator.log')}",
    ]
    process_args = list(filter(None, process_args))
    if dramsim3_cfg is not None:
        process_args = process_args + [
            "--dramsim3-result",
            str(Path(args.out_dir) / "dramsim3-logs"),
            "--dramsim3-config",
            dramsim3_cfg,
        ]

    logger.info(f'Run "{" ".join(process_args)}"')
    return_code = subprocess.Popen(process_args).wait()
    if return_code != 0:
        logger.error(f"Emulator exited with return code {return_code}")
        exit(return_code)
    logger.info(f"Emulator logs were saved in {args.out_dir}")


def run_soc(args):
    logger.info("Running configgen")
    subprocess.check_call(
        [
            "nix",
            "run",
            ".#t1.configgen",
            "--",
            f"{args.config.replace('-', '')}",
            "-t",
            f"{args.out_dir}",
        ]
    )
    elaborate_config_path = Path(f"{args.out_dir}/config.json")
    assert (
        elaborate_config_path.exists()
    ), f"cannot find elaborate config in {elaborate_config_path}"

    logger.info("Running emulator builder")
    target_name = "subsystem.emu-trace" if args.trace else "subsystem.emu"
    process_args = (
        [args.emulator_path]
        if args.emulator_path
        else ["nix", "run", f".#t1.{args.config}.{target_name}", "--"]
    )

    elf_path = (
        args.case
        if Path(args.case).exists()
        else load_elf_from_dir(args.cases_dir, args.case, args.use_individual_drv)
    )
    process_args.append(f"+init_file={elf_path}")

    elf_filename = os.path.splitext(os.path.basename(elf_path))[0]
    if args.out_dir is None:
        args.out_dir = f"./testrun/soc-emulator/{args.config}/{elf_filename}/"
        logger.info(f"Output dir set to {args.out_dir}")

    trace_filepath = args.trace_output_file or f"{args.trace_out_dir}/trace.fst"
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
