#!/usr/bin/env python3

import re
import textwrap
from io import TextIOWrapper
from typing import Dict, List, Match, Optional, Tuple, Union

from pathlib import Path
import argparse
import itertools
import json
import tomllib
import os
import subprocess
import sys

################################
### BEGIN of ninja_syntax.py ###
################################

# copied from https://github.com/ninja-build/ninja/blob/master/misc/ninja_syntax.py

def escape_path(word: str) -> str:
    return word.replace('$ ', '$$ ').replace(' ', '$ ').replace(':', '$:')

class Writer(object):
    def __init__(self, output: TextIOWrapper, width: int = 78) -> None:
        self.output = output
        self.width = width

    def newline(self) -> None:
        self.output.write('\n')

    def comment(self, text: str) -> None:
        for line in textwrap.wrap(text, self.width - 2, break_long_words=False,
                                  break_on_hyphens=False):
            self.output.write('# ' + line + '\n')

    def variable(
        self,
        key: str,
        value: Optional[Union[bool, int, float, str, List[str]]],
        indent: int = 0,
    ) -> None:
        if value is None:
            return
        if isinstance(value, list):
            value = ' '.join(filter(None, value))  # Filter out empty strings.
        self._line('%s = %s' % (key, value), indent)

    def pool(self, name: str, depth: int) -> None:
        self._line('pool %s' % name)
        self.variable('depth', depth, indent=1)

    def rule(
        self,
        name: str,
        command: str,
        description: Optional[str] = None,
        depfile: Optional[str] = None,
        generator: bool = False,
        pool: Optional[str] = None,
        restat: bool = False,
        rspfile: Optional[str] = None,
        rspfile_content: Optional[str] = None,
        deps: Optional[Union[str, List[str]]] = None,
    ) -> None:
        self._line('rule %s' % name)
        self.variable('command', command, indent=1)
        if description:
            self.variable('description', description, indent=1)
        if depfile:
            self.variable('depfile', depfile, indent=1)
        if generator:
            self.variable('generator', '1', indent=1)
        if pool:
            self.variable('pool', pool, indent=1)
        if restat:
            self.variable('restat', '1', indent=1)
        if rspfile:
            self.variable('rspfile', rspfile, indent=1)
        if rspfile_content:
            self.variable('rspfile_content', rspfile_content, indent=1)
        if deps:
            self.variable('deps', deps, indent=1)

    def build(
        self,
        outputs: Union[str, List[str]],
        rule: str,
        inputs: Optional[Union[str, List[str]]] = None,
        implicit: Optional[Union[str, List[str]]] = None,
        order_only: Optional[Union[str, List[str]]] = None,
        variables: Optional[
            Union[
                List[Tuple[str, Optional[Union[str, List[str]]]]],
                Dict[str, Optional[Union[str, List[str]]]],
            ]
        ] = None,
        implicit_outputs: Optional[Union[str, List[str]]] = None,
        pool: Optional[str] = None,
        dyndep: Optional[str] = None,
    ) -> List[str]:
        outputs = as_list(outputs)
        out_outputs = [escape_path(x) for x in outputs]
        all_inputs = [escape_path(x) for x in as_list(inputs)]

        if implicit:
            implicit = [escape_path(x) for x in as_list(implicit)]
            all_inputs.append('|')
            all_inputs.extend(implicit)
        if order_only:
            order_only = [escape_path(x) for x in as_list(order_only)]
            all_inputs.append('||')
            all_inputs.extend(order_only)
        if implicit_outputs:
            implicit_outputs = [escape_path(x)
                                for x in as_list(implicit_outputs)]
            out_outputs.append('|')
            out_outputs.extend(implicit_outputs)

        self._line('build %s: %s' % (' '.join(out_outputs),
                                     ' '.join([rule] + all_inputs)))
        if pool is not None:
            self._line('  pool = %s' % pool)
        if dyndep is not None:
            self._line('  dyndep = %s' % dyndep)

        if variables:
            if isinstance(variables, dict):
                iterator = iter(variables.items())
            else:
                iterator = iter(variables)

            for key, val in iterator:
                self.variable(key, val, indent=1)

        return outputs

    def include(self, path: str) -> None:
        self._line('include %s' % path)

    def subninja(self, path: str) -> None:
        self._line('subninja %s' % path)

    def default(self, paths: Union[str, List[str]]) -> None:
        self._line('default %s' % ' '.join(as_list(paths)))

    def _count_dollars_before_index(self, s: str, i: int) -> int:
        """Returns the number of '$' characters right in front of s[i]."""
        dollar_count = 0
        dollar_index = i - 1
        while dollar_index > 0 and s[dollar_index] == '$':
            dollar_count += 1
            dollar_index -= 1
        return dollar_count

    def _line(self, text: str, indent: int = 0) -> None:
        """Write 'text' word-wrapped at self.width characters."""
        leading_space = '  ' * indent
        while len(leading_space) + len(text) > self.width:
            # The text is too wide; wrap if possible.

            # Find the rightmost space that would obey our width constraint and
            # that's not an escaped space.
            available_space = self.width - len(leading_space) - len(' $')
            space = available_space
            while True:
                space = text.rfind(' ', 0, space)
                if (space < 0 or
                    self._count_dollars_before_index(text, space) % 2 == 0):
                    break

            if space < 0:
                # No such space; just use the first unescaped space we can find.
                space = available_space - 1
                while True:
                    space = text.find(' ', space + 1)
                    if (space < 0 or
                        self._count_dollars_before_index(text, space) % 2 == 0):
                        break
            if space < 0:
                # Give up on breaking.
                break

            self.output.write(leading_space + text[0:space] + ' $\n')
            text = text[space+1:]

            # Subsequent lines are continuations, so indent them.
            leading_space = '  ' * (indent+2)

        self.output.write(leading_space + text + '\n')

    def close(self) -> None:
        self.output.close()


def as_list(input: Optional[Union[str, List[str]]]) -> List[str]:
    if input is None:
        return []
    if isinstance(input, list):
        return input
    return [input]


def escape(string: str) -> str:
    """Escape a string such that it can be embedded into a Ninja file without
    further interpretation."""
    assert '\n' not in string, 'Ninja syntax does not allow newlines'
    # We only have one special metacharacter: '$'.
    return string.replace('$', '$$')


def expand(string: str, vars: Dict[str, str], local_vars: Dict[str, str] = {}) -> str:
    """Expand a string containing $vars as Ninja would.

    Note: doesn't handle the full Ninja variable syntax, but it's enough
    to make configure.py's use of it work.
    """
    def exp(m: Match[str]) -> str:
        var = m.group(1)
        if var == '$':
            return '$'
        return local_vars.get(var, vars.get(var, ''))
    return re.sub(r'\$(\$|\w*)', exp, string)

################################
###   END of ninja_syntax.py ###
################################

def save_file(path, content: str):
    # if os.path.exists(path):
    #     with open(path, "r") as f:
    #         if f.read() == content:
    #             return
    with open(path, "w") as f:
        f.write(content)

class CustomWriter(Writer):
    dry_run: bool
    config_toml_path: str
    config_enabled_extensions: list[str]
    env_riscv_opcodes_src: str

    def __init__(self, output, config_toml_path: str, *, dry_run=False):
        super().__init__(output)

        with open(config_toml_path, "rb") as f:
            config = tomllib.load(f)

        self.dry_run = dry_run
        self.config_toml_path = config_toml_path
        self.config_enabled_extensions = config['model']['enabled_extensions']
        self.env_riscv_opcodes_src = os.environ["RISCV_OPCODES_SRC"]

    # only invoked in non-dryrun mode
    def build_generate(w):
        os.makedirs("build/0-buildgen", exist_ok=True)

        csr_list = []
        for x in Path('csr').glob('*.asl'):
            mode, num_str, name = x.stem.split('_')
            num = int(num_str, base=16)
            csr_list.append({
                "name": name,
                "bin_addr": format(num, "012b"),
                "read_write": not mode.endswith("ro"),
            })

        csr_json_str = json.dumps({
            "csr_metadata": csr_list,
        }, indent=2)
        save_file("build/0-buildgen/csr.json", csr_json_str)

    def generate_header(w):
        w.comment('auto-generated by gen_ninja.py')
        w.newline()

        w.variable('builddir', 'build')
        w.newline()

        w.variable('RISCV_OPCODES_SRC', w.env_riscv_opcodes_src)
        w.variable('rvopcode_extension_flags', [f'--enable-instruction-sets {ext}' for ext in w.config_enabled_extensions])
        w.newline()

        w.comment("jinja_define_flags is like '-D key1=value2 -D key2=value2 ...'")
        w.variable("jinja_define_flags", "")
        w.rule(
            "jinja",
            "minijinja-cli --strict -o $out $jinja_define_flags $in",
            description="expand jinja template: $out"
        )
        w.rule(
            "jinja-toml",
            "minijinja-cli --strict -f toml -o $out $jinja_define_flags $in",
            description="expand jinja template: $out"
        )
        w.newline()

        w.comment("compile C model to static lib")
        w.variable("cflags", "")
        w.rule("cc", "cc -c $cflags -o $out $in")
        w.rule("ar", "ar rcs $out $in")
        w.newline()

    def rule_self_rebuild(w):
        w.rule(
            'buildgen',
            'python buildgen.py',
            description='regenerate build.ninja',
        )
        w.build(
            'build.ninja', 'buildgen',
            implicit="buildgen.py",
            implicit_outputs=[
                "build/0-buildgen/csr.json",
            ],
        )
        w.newline()

    def build_jinja(
        w,
        output: str,
        template: str,
        data_sources: Optional[list[str]] = None,
        defines: Optional[dict[str, str]] = None,
        *,
        flavor="json"
    ) -> str:
        match flavor:
            case "json": ninja_rule = "jinja"
            case "toml": ninja_rule = "jinja-toml"
            case _: raise RuntimeError(f"unknown jinja flavor `{flavor}`")

        if data_sources is None:
            inputs = template
        else:
            inputs = [template] + data_sources
        
        if defines is None or len(defines) == 0:
            ninja_variables = []
        else:
            define_list = [f"-D {k}={v}" for k, v in defines.items()]
            ninja_variables = [
                ("jinja_define_flags", " ".join(define_list)),
            ]

        w.build(output, ninja_rule, inputs=inputs, variables=ninja_variables)
        return output

    def generate_adhoc_asl(w) -> list[str]:
        GENASL_BASE = 'build/1-genasl'

        w.rule(
            'gen_rvopcode_instructions',
            ' '.join([
                'rvopcode', 'instructions',
                '--output', '$out',
                '--riscv-opcodes-src-dir', '$RISCV_OPCODES_SRC',
                '$rvopcode_extension_flags',
            ]),
            description='generate rvopcode instructions json',
        )
        w.build(GENASL_BASE + '/rvopcodes.json', 'gen_rvopcode_instructions')
        w.newline()

        w.rule(
            'gen_rvopcode_causes',
            ' '.join([
                'rvopcode', 'causes',
                '--output', '$out',
                '--riscv-opcodes-src-dir', '$RISCV_OPCODES_SRC',
            ]),
            description='generate rvopcode causes json',
        )
        w.build(f"{GENASL_BASE}/causes.json", 'gen_rvopcode_causes')
        w.newline()        

        outputs = [
            w.build_jinja(
                f"{GENASL_BASE}/causes.asl",
                template="template/causes.asl.j2",
                data_sources=[f"{GENASL_BASE}/causes.json"],
            ),
            w.build_jinja(
                f"{GENASL_BASE}/csr_dispatch.asl",
                template="template/csr_dispatch.asl.j2",
                data_sources=["build/0-buildgen/csr.json"],
            ),
            w.build_jinja(
                f"{GENASL_BASE}/inst_dispatch.asl",
                template="template/inst_dispatch.asl.j2",
                data_sources=["build/1-genasl/rvopcodes.json"],
            ),
            w.build_jinja(
                f"{GENASL_BASE}/inst_unimplemented.asl",
                template="template/inst_unimplemented.asl.j2",
                data_sources=["unimplemented.json"],
            ),
        ]
        w.newline()

        return outputs
    
    def generate_old_asl(w) -> list[str]:
        w.comment("generate old asl implementations")
        outputs = [
            w.build_jinja(
                f"build/1-genold/{x.name}",
                template="template/old_expand.asl.j2",
                defines = {
                    "inst": x.stem,
                    "inst_width": "16" if x.stem.startswith("c_") else "32",
                    "include_path": str(x.relative_to("template")),
                },
            )
            for x in Path("template/extensions").glob("**/*.asl")
        ]
        w.newline()

        return outputs
    
    def generate_old_rvv_asl(w) -> list[str]:
        w.comment("generate deprecated toml based rvv implementations")
        outputs = [
            w.build_jinja(
                f"build/1-genoldrvv/{x.stem}.asl",
                "template/rvv_inst.asl.j2",
                data_sources=[str(x)],
                defines={
                    "inst": x.stem,
                },
                flavor="toml",
            )
            for x in Path("template/extensions/rv_v").glob("**/*.toml")
        ]
        w.newline()

        return outputs

    def generate_new_asl(w) -> list[str]:
        w.comment("expand template instruction implementations")
        outputs = [
            w.build_jinja(f"build/1-gennew/{x.stem}", str(x))
            for x in Path("extensions").glob("**/*.asl.j2")
        ]
        w.newline()

        return outputs
    
    def scan_handwritten_asl(w) -> list[str]:
        path_list = itertools.chain(
            Path("handwritten").glob("*.asl"),
            Path("csr").glob("*.asl"),
            Path("extensions").glob("**/*.asl"),
        )

        return [str(x) for x in path_list]
    
    def generate_asl2c(w, asl_sources: list[str], basename='pokedex-sim') -> tuple[list[str], str]:
        ASL2C_CMD = [
            "asli", "--nobanner", "--batchmode",
            "--configuration=$asl2c_config",
            "--project=$asl2c_project",
            "--check-exception-markers",
            "--check-call-markers",
            "--check-constraints",
            "--runtime-checks",
            "$in"
        ]

        CMODEL_DIR = "build/2-cgen"

        ASL2C_GEN_FILES = [
            "exceptions.c",
            "exceptions.h",
            "funs.c",
            "types.h",
            "vars.c",
            "vars.h",
        ]

        cmodel_files = [f"{CMODEL_DIR}/{basename}_{x}" for x in ASL2C_GEN_FILES]

        w.comment("compile ASL to C model")        
        w.build_jinja(
            f"{CMODEL_DIR}/asl2c.prj",
            "aslbuild/asl2c.prj.j2",
            defines={
                "output_dir": CMODEL_DIR,
                "basename": basename,
            }
        )
        w.rule("asl2c", " ".join(ASL2C_CMD), description="asli: compile ASL to C model")
        w.build(
            cmodel_files,
            "asl2c",
            inputs=asl_sources,
            variables=[
                ("asl2c_config", "aslbuild/project.json"),
                ("asl2c_project", "build/2-cgen/asl2c.prj"),
            ],

            # implicit inputs
            implicit=[
                "aslbuild/project.json",
                "build/2-cgen/asl2c.prj",
            ],

            # let ninja create "dumps" dir, otherwise asli will complain
            implicit_outputs=f"{CMODEL_DIR}/dumps/log.21.quit.asl",
        )
        w.newline()

        CLIB_DIR = "build/3-clib"
        clib = f"{CLIB_DIR}/libpokedex_model.a"

        ofiles = []
        for x in ASL2C_GEN_FILES:
            if x.endswith(".c"):
                ofile = f"{CLIB_DIR}/{basename}_{x}.o"
                w.build(ofile, "cc", f"{CMODEL_DIR}/{basename}_{x}")
                ofiles.append(ofile)
        w.build(clib, "ar", ofiles)
        w.newline()

        return cmodel_files, clib

    def generate(w):
        w.generate_header()

        w.rule_self_rebuild()

        GENASL_SRCS = w.generate_adhoc_asl()
        GENNEW_SRCS = w.generate_new_asl()
        GENOLD_SRCS = w.generate_old_asl()
        GENOLDRVV_SRCS = w.generate_old_rvv_asl()
        HANDWRITTEN_SRCS = w.scan_handwritten_asl()

        ALL_ASL_SRCS = GENASL_SRCS + \
            GENOLD_SRCS + \
            GENOLDRVV_SRCS + \
            GENNEW_SRCS + \
            HANDWRITTEN_SRCS

        CMODEL_FILES, CLIB_FILE = w.generate_asl2c(ALL_ASL_SRCS)

        w.build("cmodel", "phony", CMODEL_FILES)
        w.build("clib", "phony", CLIB_FILE)
        w.default(["cmodel", "clib"])

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="print build.jinja to stdout and suppress other outputs",
    )

    args = parser.parse_args()

    CONFIG_TOML_PATH = "config.toml"

    if args.dry_run:
        w = CustomWriter(sys.stdout, CONFIG_TOML_PATH, dry_run=True)
        w.generate()
    else:
        with open("build.ninja", "w") as f:
            w = CustomWriter(f, CONFIG_TOML_PATH)
            w.build_generate()
            w.generate()
