#!/usr/bin/env python3

import argparse
import json
import glob
import subprocess
from typing import cast, TypedDict
from pathlib import Path

RED_START = "\x1b[31m"
GREEN_START = "\x1b[32m"
RESET = "\x1b[0m"


class ProcessConfig(TypedDict):
    timeout: int
    args: list[str]


class BatchrunConfig(TypedDict):
    elf_path_glob: str
    mmio_end_addr: str
    spike: ProcessConfig
    pokedex: ProcessConfig


class DifftestResult(TypedDict):
    is_same: bool
    context: str


def run_spike(elf: Path, spike: ProcessConfig) -> Path:
    args = ["spike"] + spike["args"] + [elf]
    result = subprocess.run(
        args, stderr=subprocess.PIPE, timeout=spike["timeout"], check=True
    )
    spike_log = result.stderr.strip().decode()
    log_path = Path(f"{elf.stem}-spike-commits.log")
    with open(log_path, "w+") as log_file:
        log_file.write(spike_log)

    return log_path


def run_pokedex(elf: Path, pokedex: ProcessConfig) -> Path:
    log_path = Path(f"{elf.stem}-pokedex-trace-events.jsonl")

    args = ["pokedex", "-vvv", "--output-log-path", log_path] + pokedex["args"] + [elf]

    subprocess.check_call(args, timeout=pokedex["timeout"])

    return log_path


def run_difftest(
    elf: Path, spike_log_path: Path, pokedex_log_path: Path, mmio_end_addr: str
) -> Path:
    difftest_result_path = Path(f"{elf.stem}-difftest-result.json")
    subprocess.check_call(
        [
            "difftest",
            "--spike-log-path",
            spike_log_path,
            "--pokedex-log-path",
            pokedex_log_path,
            "--mmio-address",
            mmio_end_addr,
            "--output-path",
            difftest_result_path,
        ]
    )
    return difftest_result_path


def parse_args():
    parser = argparse.ArgumentParser(
        prog="batchrun", description="batch run ELFs with differential tests"
    )
    parser.add_argument("-c", "--config")
    args = parser.parse_args()
    return args


if __name__ == "__main__":
    args = parse_args()

    config: BatchrunConfig
    with open(args.config, "r") as f:
        raw_config = f.read()
        config = cast(BatchrunConfig, json.loads(raw_config))

    context = []

    for elf in glob.glob(config["elf_path_glob"], recursive=True):
        elf_path = Path(elf)
        spike_log_path = run_spike(elf_path, config["spike"])
        pokedex_log_path = run_pokedex(elf_path, config["pokedex"])

        difftest_result = run_difftest(
            elf_path, spike_log_path, pokedex_log_path, config["mmio_end_addr"]
        )
        with open(difftest_result, "r") as file:
            result = cast(DifftestResult, json.loads(file.read()))
            if result["is_same"]:
                print(f"** {GREEN_START}PASS{RESET}   {elf_path.name} **")
            else:
                print(f"** {RED_START}FAIL{RESET}   {elf_path.name} **")
                print(result["context"])
                context.append(elf_path)

    if len(context) != 0:
        print("** batch run fail on some tests **")
        with open("batch-run-result.json", "w") as file:
            file.write(json.dumps(context))
        exit(1)
    else:
        print("** all tests pass **")
        exit(0)
