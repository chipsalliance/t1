#!/usr/bin/env python3

import subprocess
import argparse
import os
import json


class DifftestRunner:
    spike: str
    default_spike_args: list[str]
    pokedex: str
    default_pokedex_args: list[str]

    def __init__(self) -> None:
        self.spike = os.environ["SPIKE"]
        self.pokedex = os.environ["POKEDEX"]

        march = os.environ["MARCH"]
        pokedex_config = os.environ["POKEDEX_CONFIG"]

        self.default_spike_args = [
            f"--isa={march}",
            "--priv=m",
            "--log-commits",
            "-p1",
            "--hartids=0",
            "--triggers=0",
            "-m0x80000000:0x20000000,0x40000000:0x1000",
        ]

        self.default_pokedex_args = ["--config-path", pokedex_config]

    def run_spike(self, elf_path: str, log_path: str):
        try:
            subprocess.check_call(
                [self.spike] + self.default_spike_args + [f"--log={log_path}", elf_path]
            )
        except subprocess.CalledProcessError:
            print("Critical: spike crash!")
            exit(1)

    def run_pokedex(self, elf_path: str, log_path: str):
        try:
            subprocess.run(
                [self.pokedex, "run"]
                + self.default_pokedex_args
                + [f"--output-log-path={log_path}", elf_path],
                capture_output=True,
                text=True,
            ).check_returncode()
        except subprocess.CalledProcessError as e:
            print(f"Critical: pokedex crash!\nSTDOUT:\n{e.stdout}\nSTDERR:\n{e.stderr}")
            exit(1)

    def difftest(
        self,
        elf_path: str,
        spike_log_path: str,
        pokedex_log_path: str,
        result_path: str,
    ):
        self.run_spike(elf_path, spike_log_path)
        self.run_pokedex(elf_path, pokedex_log_path)
        # run difftest
        subprocess.check_call(
            [
                self.pokedex,
                "difftest",
                "--spike-log-path",
                spike_log_path,
                "--pokedex-log-path",
                pokedex_log_path,
                "--output-path",
                result_path,
            ]
        )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--elf")
    parser.add_argument("--spike-log")
    parser.add_argument("--pokedex-log")
    parser.add_argument("--diff-result")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    if args.check and args.diff_result:
        with open(args.diff_result, "rb") as result_file:
            result = json.load(result_file)
            # If test fail, print diff notes
            assert result["is_same"], "\n".join(result["diff_notes"])
        exit(0)

    diff_runner = DifftestRunner()

    if args.elf and args.diff_result and args.spike_log and args.pokedex_log:
        diff_runner.difftest(
            args.elf, args.spike_log, args.pokedex_log, args.diff_result
        )
        exit(0)

    print("Critial: invalid argument combination!")
    exit(1)


if __name__ == "__main__":
    main()
