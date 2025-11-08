#!/usr/bin/env python3

import argparse
import csv
import json
import re
import os
from pathlib import Path

from .riscv_opcodes import RiscvOpcodes

def read_file(path):
    with open(path) as f:
        return f.read()

def read_file_json(path):
    with open(path) as f:
        return json.load(f)

def write_file_json(path, object, indent=2):
    with open(path, 'w') as f:
        json.dump(object, f, indent=indent)
        f.write("\n")

class CheckFailError(Exception):
    def __init__(self, message: str):
        super().__init__(message)

def gen_causes(db: RiscvOpcodes, is_check: bool):
    CAUSE_DATA_FILE = "data_files/causes.json"

    rvopcode_causes = db.parse_causes()
    # print(rvopcode_causes)

    if is_check:
        # check mode
        cause_json = read_file_json(CAUSE_DATA_FILE)
        for cause in cause_json["exceptions"]:
            name = cause["name"]
            code = cause["code"]

            if code not in rvopcode_causes:
                raise CheckFailError(f"exception code {code} not defined in causes.csv")
            if name != rvopcode_causes[code]:
                raise CheckFailError(f"exception code {code} name mismatch: '{name}' in causes.json(our); {rvopcode_causes[code]} in causes.csv")
    else:
        # update mode
        cause_json = {
            "exceptions": [
                {
                    "name": name,
                    "code": code,
                }
                for code, name in rvopcode_causes.items()
            ],
        }
        print(f"write to file: {CAUSE_DATA_FILE}")
        write_file_json(CAUSE_DATA_FILE, cause_json)

def gen_csr(is_check: bool):
    CSR_DATA_FILE = "data_files/csr.json"

    # TODO: cross check with riscv opcodes

    csr_list = []
    for x in Path('csr').glob('*.asl'):
        mode, addr_str, name = x.stem.split('_')
        addr = int(addr_str, base=16)
        csr_list.append({
            "name": name,
            "mode": mode,
            "addr": addr,
            "bin_addr": format(addr, "012b"),
            "read_write": not mode.endswith("ro"),
        })
    csr_list.sort(key=lambda x: x["addr"])

    if is_check:
        # check mode
        csr_json = read_file_json(CSR_DATA_FILE)
        if csr_list != csr_json["csr_metadata"]:
            raise CheckFailError("csr table misatch, run ./datagen.py to update")
    else:
        # update mode
        csr_json = {
            "csr_metadata": csr_list,
        }
        print(f"write to file: {CSR_DATA_FILE}")
        write_file_json(CSR_DATA_FILE, csr_json)

def gen_instructions(db: RiscvOpcodes, is_check: bool):
    INSTR_DATA_FILE = "data_files/inst_encoding.json"

    # FIXME: read from config.toml
    EXTENSIONS = [
        'rv_i',
        'rv_m',
        'rv_a',
        'rv_c',
        'rv_f',
        'rv_v',
        'rv_zicsr', 
        'rv_zifencei',
        'rv32_i',
        'rv32_c',
        'rv32_c_f',
        'rv_system',
    ]

    rvopcode_instrs = db.parse_instructions(EXTENSIONS)

    inst_encoding = []
    cinst_encoding = []
    for name, data in rvopcode_instrs.items():
        match data["encoding"][30:32]:
            case "11":
                inst_encoding.append({
                    "name": name,
                    "encoding": data["encoding"],
                    "extension": ",".join(data["extension"])
                })
            case "00" | "01" | "10":
                # the C inst encoding in riscv opcodes is wired ...
                assert data["encoding"].startswith("-" * 16)
                cinst_encoding.append({
                    "name": name,
                    "encoding": data["encoding"][16:32],
                    "extension": ",".join(data["extension"])
                })
            case _: raise CheckFailError(f"riscv opcodes: fail to parse inst '{name}' with encoding {data["encoding"]}")
    inst_encoding.sort(key=lambda x: x["name"])
    cinst_encoding.sort(key=lambda x: x["name"])

    if is_check:
        # check mode
        instr_json = read_file_json(INSTR_DATA_FILE)
        if inst_encoding != instr_json["inst_encoding"]:
            raise CheckFailError("inst table misatch, run ./datagen.py to update")
        if cinst_encoding != instr_json["cinst_encoding"]:
            raise CheckFailError("inst table misatch, run ./datagen.py to update")
    else:
        # update mode
        csr_json = {
            "inst_encoding": inst_encoding,
            "cinst_encoding": cinst_encoding,
        }
        print(f"write to file: {INSTR_DATA_FILE}")
        write_file_json(INSTR_DATA_FILE, csr_json)

def run_all(
    is_check: bool,
    riscv_opcodes_src: str | None = None
):
    db = RiscvOpcodes(riscv_opcodes_src)

    gen_causes(db, is_check)
    gen_csr(is_check)
    gen_instructions(db, is_check)

# run as "python -m scripts.datagen"
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="generate various data files under data_files/")
    parser.add_argument(
        "--check",
        action="store_true",
        help="run in check mode",
    )
    parser.add_argument(
        "--sentinel",
        required=False,
        help=""
    )
    parser.add_argument(
        "--riscv_opcodes_src",
        required=False,
        help="path to riscv opcodes repo, default to env $RISCV_OPCODES_SRC",
    )

    args = parser.parse_args()

    run_all(
        is_check=args.check,
        riscv_opcodes_src=args.riscv_opcodes_src,
    )

    if args.sentinel is not None:
        with open(args.sentinel, 'w'):
            pass
