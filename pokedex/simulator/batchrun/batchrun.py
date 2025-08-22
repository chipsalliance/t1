#!/usr/bin/env python3

import argparse
import json
import glob
import subprocess
import re
from typing import cast, TypedDict
from pathlib import Path

RED = "\x1b[31m"
GREEN = "\x1b[32m"
YELLOW = "\x1b[33m"
RESET = "\x1b[0m"


class ProcessConfig(TypedDict):
    timeout: int
    args: list[str]


class IgnoreTest(TypedDict):
    pattern: str
    reason: str


class BatchrunConfig(TypedDict):
    elf_path_glob: str
    mmio_end_addr: str
    ignore_tests: list[IgnoreTest]
    spike: ProcessConfig
    pokedex: ProcessConfig


class DifftestResult(TypedDict):
    is_same: bool
    context: str


class IgnoreTestExt:
    def __init__(self, ignore_tests: list[IgnoreTest]) -> None:
        self._pat = [
            (re.compile(meta["pattern"]), meta["reason"]) for meta in ignore_tests
        ]

    def any(self, text: str) -> str | None:
        for p, reason in self._pat:
            if p.fullmatch(text) is not None:
                return reason
        return None


def run_spike(elf: Path, spike: ProcessConfig) -> Path:
    log_path = Path(f"{elf.stem}-spike-commits.log")
    args = ["spike"] + spike["args"] + [f"--log={log_path}", elf]
    subprocess.check_call(args, timeout=spike["timeout"])

    return log_path


def run_pokedex(elf: Path, pokedex: ProcessConfig) -> Path:
    log_path = Path(f"{elf.stem}-pokedex-trace-events.jsonl")
    args = ["pokedex", "-vvv", "--output-log-path", log_path] + pokedex["args"] + [elf]
    output = subprocess.run(args, stdout=subprocess.PIPE, timeout=pokedex["timeout"])
    if output.returncode != 0:
        print(f"{elf} run fail")
        print("-" * 80)
        print(str(output.stdout, encoding="utf-8"))
        print("-" * 80)
        exit(1)

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

    ignore_match = IgnoreTestExt(config["ignore_tests"])
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
                print(f"** {GREEN}PASS{RESET}   {elf_path.name}")
            else:
                reason = ignore_match.any(elf_path.name)
                if reason is not None:
                    print(f"\n** {YELLOW}F-IGNORE{RESET}  {elf_path.name}: {reason}\n")
                else:
                    print(f"\n** {RED}FAIL{RESET}   {elf_path.name}\n")
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
