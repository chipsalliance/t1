#!/usr/bin/env python3

import argparse
import json
from pathlib import Path

from .riscv_opcodes import RiscvOpcodes


def read_file(path):
    with open(path) as f:
        return f.read()


def read_file_json(path):
    with open(path) as f:
        return json.load(f)


def write_file_json(path, object, indent=2):
    with open(path, "w") as f:
        json.dump(object, f, indent=indent)
        f.write("\n")


class CheckFailError(Exception):
    def __init__(self, message: str):
        super().__init__(message)


class DataGenerator:
    def __init__(
        self,
        root: Path,
        riscv_opcodes_src: str | None = None,
        enable_exts: list[str] = [],
    ):
        self.root = root
        self.db = RiscvOpcodes(riscv_opcodes_src)
        self.is_check = False
        self.enable_exts = enable_exts

    def gen_instructions(self):
        INSTR_DATA_FILE = self.root / "inst_encoding.json"
        print(f"[datagen] Generating for extensions: {", ".join(self.enable_exts)}")

        rvopcode_instrs = self.db.parse_instructions(self.enable_exts)

        inst_encoding = []
        cinst_encoding = []
        for name, data in rvopcode_instrs.items():
            match data["encoding"][30:32]:
                case "11":
                    inst_encoding.append(
                        {
                            "name": name,
                            "encoding": data["encoding"],
                            "extension": ",".join(data["extension"]),
                        }
                    )
                case "00" | "01" | "10":
                    # the C inst encoding in riscv opcodes is wired ...
                    assert data["encoding"].startswith("-" * 16)
                    cinst_encoding.append(
                        {
                            "name": name,
                            "encoding": data["encoding"][16:32],
                            "extension": ",".join(data["extension"]),
                        }
                    )
                case _:
                    raise CheckFailError(
                        f"riscv opcodes: fail to parse inst '{name}' with encoding {data["encoding"]}"
                    )
        inst_encoding.sort(key=lambda x: x["name"])
        cinst_encoding.sort(key=lambda x: x["name"])

        if self.is_check:
            # check mode
            instr_json = read_file_json(INSTR_DATA_FILE)
            if inst_encoding != instr_json["inst_encoding"]:
                raise CheckFailError(
                    "inst table misatch, run python3 -m scripts.datagen to update"
                )
            if cinst_encoding != instr_json["cinst_encoding"]:
                raise CheckFailError(
                    "inst table misatch, run python3 -m scripts.datagen to update"
                )
        else:
            # update mode
            csr_json = {
                "inst_encoding": inst_encoding,
                "cinst_encoding": cinst_encoding,
            }
            print(f"write to file: {INSTR_DATA_FILE}")
            write_file_json(INSTR_DATA_FILE, csr_json)

    def run_all(self, is_check: bool):
        self.is_check = is_check

        self.gen_instructions()


# run as "python -m scripts.datagen"
if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="generate various data files under data_files/"
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="run in check mode",
    )
    parser.add_argument(
        "--extensions",
        nargs="+",
        help="List of extensions enabled",
    )
    parser.add_argument("--sentinel", required=False, help="")
    parser.add_argument(
        "--riscv_opcodes_src",
        required=False,
        help="path to riscv opcodes repo, default to env $RISCV_OPCODES_SRC",
    )
    parser.add_argument("--root", default="data_files", help="root dir to save file")

    args = parser.parse_args()

    generator = DataGenerator(
        root=Path(args.root),
        riscv_opcodes_src=args.riscv_opcodes_src,
        enable_exts=args.extensions,
    )
    generator.run_all(is_check=args.check)

    if args.sentinel is not None:
        with open(args.sentinel, "w"):
            pass
