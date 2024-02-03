#!/usr/bin/env python3

from argparse import ArgumentParser
from pathlib import Path
import os
import logging
import subprocess
import json
import tempfile
import shutil
from pprint import pprint

from _utils import ColorFormatter

logger = logging.getLogger("t1-run-test")
ch = logging.StreamHandler()
ch.setFormatter(ColorFormatter())
logger.addHandler(ch)


def main():
    parser = ArgumentParser()

    parser.add_argument(
        "--list-config", action="store_true", help="List all config name"
    )
    parser.add_argument("--explain-config", default=None, help="Explain config detail")

    subparsers = parser.add_subparsers(help="sub-commands help", dest="emu_type")

    # Add sub-commands
    verilator_args_parser = subparsers.add_parser(
        "ip", help="ip emulator help"
    )  # TODO: rename to ip
    subsystem_args_parser = subparsers.add_parser(
        "subsystem", help="subsystem emulator help"
    )

    # Register common args
    for subparser in (verilator_args_parser, subsystem_args_parser):
        subparser.add_argument("case", help="Case name alias or a path to ELF file")
        subparser.add_argument(
            "-c",
            "--config",
            default="squirtle",
            help="config name, default to squirtle. Use --list-config to get all configs",
        )
        subparser.add_argument(
            "--trace", action="store_true", help="enable trace file dumping"
        )
        subparser.add_argument(
            "--emulator-path",
            default=None,
            help="path to the subsystem emulator, use nix generated one if unspecified",
        )
        subparser.add_argument(
            "--cases-dir",
            help="path to testcases, default to TEST_CASES_DIR environment",
        )
        subparser.add_argument(
            "--use-individual-drv",
            help="use .#t1.cases.<case_type>.<case_name> instead of .#t1.cases.all",
            action="store_true",
        )
        subparser.add_argument(
            "--force-x86",
            help="use cases built in x86, for non-x86 machines not capable of building cases",
            action="store_true",
        )
        subparser.add_argument(
            "--out-dir",
            default=None,
            help="path to save results",  # TODO: give a consistent behavior for both verilate and subsystem emulator
        )
        subparser.add_argument(
            "--base-out-dir",
            default=None,
            help="save result files in {base_out_dir}/{config}/{case}",
        )
        subparser.add_argument(
            "-v", "--verbose", action="store_true", help="set loglevel to debug"
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
        "--cosim-timeout", default=400000, help="set cosim timeout"
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

    # Register subsystem emulator args
    subsystem_args_parser.add_argument(
        "--trace-out-file",
        default="None",
        help="path for storing trace file, default to <output-dir>/trace.fst",
    )

    # Run
    args = parser.parse_args()

    if args.list_config:
        subprocess.Popen(
            ["nix", "run", "--no-warn-dirty", ".#t1.configgen", "--", "listConfigs"]
        ).wait()
        return

    if args.explain_config is not None:
        config_outputpath = tempfile.mkdtemp()
        configgen_args = [
            "nix",
            "run",
            "--no-warn-dirty",
            ".#t1.configgen",
            "--",
            f"{args.explain_config}",
            "-t",
            f"{config_outputpath}",
        ]
        subprocess.Popen(configgen_args).wait()

        with open(f"{config_outputpath}/config.json") as json_data:
            pprint(json.load(json_data)["parameter"])

        shutil.rmtree(config_outputpath)
        return

    run_test(args)


# Try to search ELF from the given directory
def load_elf_from_dir(config, cases_dir, case_name, use_individual_drv, force_x86):
    cases_attr_name = "cases-x86" if force_x86 else "cases"
    if cases_dir is None:
        if env_case_dir := os.environ.get("TEST_CASES_DIR"):
            cases_dir = env_case_dir
        else:
            nix_args = [
                "nix",
                "build",
                "--no-link",
                "--print-out-paths",
                "--no-warn-dirty",
            ]
            if use_individual_drv:
                split_idx = case_name.rfind("-")
                case_true_name, case_type = (
                    case_name[:split_idx].replace(".", "-"),
                    case_name[split_idx + 1 :],
                )
                nix_args.append(
                    f".#t1.{config}.{cases_attr_name}.{case_type}.{case_true_name}"
                )
            else:
                nix_args.append(f".#t1.{config}.{cases_attr_name}.all")
            logger.info(f'Get ELFs dir by cmd: "{" ".join(nix_args)}"')
            cases_dir = subprocess.check_output(nix_args).strip().decode("UTF-8")

    cases_dir = Path(cases_dir)
    logger.info(f"Reading ELFs in {cases_dir}")

    case_config_path = (
        cases_dir / f"{case_name}.json"
        if use_individual_drv
        else cases_dir / "configs" / f"{case_name}.json"
    )
    assert case_config_path.exists(), f"cannot find case config in {case_config_path}"
    config = json.loads(case_config_path.read_text())

    case_elf_path = cases_dir / config["elf"]["path"]
    assert case_elf_path.exists(), f"cannot find case elf in {case_elf_path}"

    return case_elf_path


def run_test(args):
    emu_type = args.emu_type
    if emu_type is None or len(emu_type) == 0:
        print("Either `ip` or `subsystem` are not given")
        return

    if args.verbose:
        logger.setLevel(logging.DEBUG)
    else:
        logger.setLevel(logging.INFO)

    # determine out_dir
    if args.out_dir is None:
        if args.base_out_dir is not None:
            args.out_dir = f"{args.base_out_dir}/{args.config}/{args.case}"
        else:
            args.out_dir = f"./testrun/{emu_type}emu/{args.config}/{args.case}"
        Path(args.out_dir).mkdir(exist_ok=True, parents=True)

    # locate case path
    case_elf_path = (
        args.case
        if Path(args.case).exists()
        else load_elf_from_dir(
            args.config,
            args.cases_dir,
            args.case,
            args.use_individual_drv,
            args.force_x86,
        )
    )

    elaborate_config_path = Path(f"{args.out_dir}/config.json")
    if elaborate_config_path.exists():
        os.remove(elaborate_config_path)
    configgen_args = [
        "nix",
        "run",
        "--no-warn-dirty",
        ".#t1.configgen",
        "--",
        f"{args.config}",
        "-t",
        f"{args.out_dir}",
    ]
    logger.info(f'Get emulator config by cmd: "{" ".join(configgen_args)}"')
    subprocess.Popen(configgen_args).wait()
    assert (
        elaborate_config_path.exists()
    ), f"cannot find elaborate config in {elaborate_config_path}"

    target_name = f"{emu_type}.emu-trace" if args.trace else f"{emu_type}.emu"

    emu_args = None

    def optionals(cond, items):
        return items if cond else []

    if emu_type == "ip":
        dramsim3_cfg = args.dramsim3_cfg
        rtl_config = json.loads(elaborate_config_path.read_text())
        tck = 10**3 / args.frequency
        emu_args = (
            [
                "--elf",
                str(case_elf_path),
                "--wave",
                str(Path(args.out_dir) / "wave.fst"),
                "--timeout",
                str(args.cosim_timeout),
                "--tck",
                str(tck),
                "--perf",
                str(Path(args.out_dir) / "perf.txt"),
                "--vlen",
                str(rtl_config["parameter"]["vLen"]),
                "--dlen",
                str(rtl_config["parameter"]["dLen"]),
                # TODO: this will be refactored soon to support multiple LSU
                "--tl_bank_number",
                str(rtl_config["parameter"]["lsuInstantiateParameters"][0]["banks"])
            ]
            + optionals(args.no_logging, ["--no-logging"])
            + optionals(args.no_file_logging, ["--no-file-logging"])
            + optionals(args.no_console_logging, ["--no-console-logging"])
            + optionals(
                args.out_dir, [f"--log-path={str(Path(args.out_dir) / 'emulator.log')}"]
            )
            + optionals(
                dramsim3_cfg is not None,
                [
                    "--dramsim3-result",
                    str(Path(args.out_dir) / "dramsim3-logs"),
                    "--dramsim3-config",
                    dramsim3_cfg,
                ],
            )
        )

    elif emu_type == "subsystem":
        emu_args = [f"+init_file={case_elf_path}"]
        if args.trace:
            trace_file_path = args.trace_out_file or f"{args.out_dir}/trace.fst"
            emu_args.append(f"+trace_file={trace_file_path}")

    else:
        assert False, f"unknown emutype {emu_type}"

    process_args = (
        [args.emulator_path]
        if args.emulator_path
        else [
            "nix",
            "run",
            "--no-warn-dirty",
            f".#t1.{args.config}.{target_name}",
            "--",
        ]
    ) + emu_args

    logger.info(f'Run {emu_type} emulator with cmd: "{" ".join(process_args)}"')
    return_code = subprocess.Popen(process_args).wait()

    if return_code != 0:
        logger.error(f"Emulator exited with return code {return_code}")
        exit(return_code)
    logger.info(f"Emulator logs were saved in {args.out_dir}")


if __name__ == "__main__":
    main()
