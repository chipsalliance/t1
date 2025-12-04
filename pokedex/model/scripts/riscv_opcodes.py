import re
import os

from pathlib import Path

from .riscv_opcodes_util import SingleInstr, create_inst_dict


class RiscvOpcodes:
    root_dir: Path

    def __init__(self, root_dir: str | None):
        if root_dir is None:
            self.root_dir = Path(os.environ["RISCV_OPCODES_SRC"])
        else:
            self.root_dir = Path(root_dir)

    # return dict of { code: name }
    def parse_causes(self) -> dict[int, str]:
        causes = {}

        # '0x01, "fetch access"' -> ('0x01', 'fetch access')
        pat = re.compile(r'(0x[0-9a-fA-F]+), "(.*)"')

        with open(self.root_dir / "causes.csv") as f:
            file_str = f.read()
        for line in file_str.splitlines():
            match = pat.fullmatch(line)
            assert (
                match is not None
            ), f"failed to parse riscv opcodes cause.csv, line = '{line}'"

            code_str, name = match.groups()
            code = int(code_str, 0)

            assert code not in causes
            causes[code] = name

        return causes

    def parse_instructions(self, extensions: list[str]) -> dict[str, SingleInstr]:
        extensions = sorted(extensions)
        return create_inst_dict(self.root_dir, file_filter=extensions)
