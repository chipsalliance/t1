#import "@preview/fletcher:0.5.8" as fletcher: diagram, edge, node
#import fletcher.shapes: hexagon, house

#let darkmode_enable = sys.inputs.keys().any(k => k == "enable-darkmode")
#let theme = (
  bg: if darkmode_enable { black } else { white },
  fg: if darkmode_enable { white } else { black },
  bg_grey: if darkmode_enable { luma(100) } else { luma(240) },
  bg_raw: if darkmode_enable { rgb("#1d2433") } else { luma(240) },
)

#set page(fill: theme.bg)
#set text(fill: theme.fg)

// Title Page
#v(30%)
#line(length: 100%, stroke: theme.fg)
#align(center, text(size: 3em, weight: 800, "Document for Pokedex"))
#line(length: 100%, stroke: theme.fg)
#align(center, text(1.5em, weight: 500, datetime.today().display()))

#pagebreak()

// Contents
#set heading(numbering: "1.1")
#show heading: set block(above: 1.8em, below: 1.5em)
#show heading.where(level: 1): it => pagebreak(weak: true) + it
#show link: underline
#show raw.where(block: true): it => {
  set align(center)
  set block(
    fill: theme.bg_raw,
    inset: 1em,
    radius: 5pt,
    width: 95%,
  )
  text(fill: theme.fg, it)
}
#show raw.where(block: false): it => box(
  fill: theme.bg_raw,
  inset: (x: 0.3em, y: 0pt),
  outset: (y: 0.3em),
  radius: 0.2em,
  text(fill: theme.fg, it),
)

#let notes(body) = {
  show quote.where(block: true): block.with(
    inset: 0.5em,
    stroke: (left: 3pt + blue, rest: none),
  )
  quote(block: true, body)
}

#show figure: it => {
  v(2em)
  it
  v(2em)
}

// Add some indent to the list item
#show enum: it => pad(left: 1em, it)
#show list: it => pad(left: 1em, it)

#outline()

= The Pokedex Project

== Introduction
Pokedex is an implementation of the RISC-V instruction set architecture (ISA).
It provides a simple, maintainable, and flexible ISA design that allows
developers to easily add new instructions or make architectural changes—capabilities
that are often difficult to achieve with existing tools like `riscv/sail-riscv`
or Spike (`riscv/riscv-isa-sim`).

The Pokedex project comprises three main components:
- A formal specification written in the ARM Architecture Specification Language (*The ISA Model*).
- A *simulator* written in Rust, which provides a system platform for co-simulation with the ISA model.
- A *differential test framework* to cross-validate architectural states against Spike and RTL.

You can think of _The ISA Model_ as the code that implements the logic defined
by the RISC-V ISA Specification. _The Simulator_ acts as the host environment;
it handles the necessary system-level operations (such as loading ELF files)
that are not covered by the ISA specification but are required to run programs.
Finally, the _Differential Test Framework_ provides the tools necessary to
validate and guarantee the correctness of _The ISA Model_.

#let blob(pos, label, tint: white, ..args) = node(
  pos,
  align(center, label),
  width: 28mm,
  fill: if darkmode_enable { tint.darken(50%) } else { tint.lighten(60%) },
  stroke: 1pt + tint.darken(20%),
  corner-radius: 5pt,
  ..args,
)

#let pokedex_arch = diagram(
  spacing: 8mm,
  cell-size: (8mm, 10mm),
  edge-stroke: 1pt + theme.fg,
  edge-corner-radius: 5pt,
  mark-scale: 70%,

  blob((0, 0), [ASL Source Code], tint: green),
  blob((1, 0), [Softfloat Library], tint: yellow, shape: hexagon),

  blob((0, 1), [ASL Share Library], tint: green, shape: hexagon),
  edge((0, 0), (0, 1), "->"),
  edge((1, 0), (1, 0.5), "l"),

  blob((2, 0), [Rust Source Code], tint: blue),
  blob((2, 1), [Simulator], tint: blue, shape: hexagon),
  edge((2, 0), (2, 1), "->"),

  edge((0, 1), (2, 1), "-->", label: "dl-load"),

  blob((2, 2), [Commits JSON Log], tint: gray),
  edge((2, 1), (2, 2), "->"),

  blob((3, 0), [RISC-V ELF], tint: purple, shape: hexagon),
  edge((3, 0), (3, 0.5), (2.5, 0.5), (2.5, 1.5), (2, 1.5), "-->"),

  blob((3, 1), [Spike], tint: blue, shape: hexagon),
  edge((3, 0), (3, 1), "-->", label: "file load"),

  edge((3, 1), (3, 2), "->"),
  blob((3, 2), [Spike Commits], tint: gray),

  blob((2.5, 3), [difftest], tint: blue, shape: house),
  edge((2, 2), (2.5, 3), "-->"),
  edge((3, 2), (2.5, 3), "-->"),
)

#align(center, figure(pokedex_arch, caption: [Pokedex Architecture])) <pokedex-architecture-figure>

The primary goal of the Pokedex project is to serve as a "source of truth" for
the ISA's behavior. This enables co-simulation to identify bugs in Register
Transfer Level (RTL) designs.

#notes[
  *Why the name "Pokedex"?*

  Our T1 testbench supports a collection of micro-architecture designs, each
  named after a Pokémon. We chose the name "Pokedex" to align with this theme.

  In the Pokémon world, a Pokédex is an essential tool for understanding the
  creatures you interact with. In that same spirit, this project provides the
  tools necessary to understand, maintain, and improve our T1 architectures.
]

== What's Coming
This document is intended for readers with a basic understanding of software
programming. It covers the entire Pokedex architecture and explains key design
choices. By the end of this guide, readers will be able to modify and verify
the whole project.

== Suggest Reading

- *ASL Document* hosted by Intel Lab: https://intellabs.github.io/asl-interpreter/asl_reading.html
- *ASL Specification*: https://developer.arm.com/documentation/ddi0626/latest
- *RISC-V ISA Specification*: https://github.com/riscv/riscv-isa-manual/
- *ASL Prelude Reference*: https://github.com/IntelLabs/asl-interpreter/blob/master/prelude.asl
- *herdtools7* (The "official" ASL): https://github.com/herd/herdtools7/tree/ASLRefALP3.1/asllib

== Compatibility

Note that we currently use the Intel Labs fork of the ASL Interpreter, which
deviates slightly from the official ASL specification. A migration to
_herdtools7_ is planned, pending support for C code generation in that toolchain.

== System Requirement

We use Nix as our primary build system. It orchestrates the build systems for
each module and produces final artifacts without requiring manual dependency
management.

The only requirement is a system with Nix installed. You can follow the installation
guide here: https://nixos.org/download.
(Note: You do not need the full NixOS operating system; only the Nix package manager
CLI and daemon are required).

After installation, add the following configuration to `~/.config/nix/nix.conf`:

```conf
extra-experimental-features = flakes nix-command pipe-operators
```

Writing Nix code is outside the scope of this guide. However, the Pokedex
project configuration is stable, so you generally will not need to modify Nix
files. If you encounter build issues, please file an issue report.

= ASL Model

We use ARM's Architecture Specification Language (ASL) to formally describe the
RISC-V ISA. The core logic is organized within the `model/` directory:

- `aslbuild/`: Contains configuration files for `asl2c` to generate C code.
- `csr/`: Contains code snippets for individual Control and Status Register (CSR) implementations.
- `csrc/`: Contains C source code for ABI compatibility and module integration.
- `data_files/`: Pre-generated files containing deserializable data for the decoder.
- `extensions/`: Holds code snippets defining the semantics for each instruction, organized by ISA extension.
- `handwritten/`: Includes foundational, manually written code, such as architectural state declarations and helper libraries.
- `scripts/`: Includes scripts to generate Ninja build files, data files, etc.
- `template/`: Legacy code and templates, will be eliminate after migration.

== Quick Glance at the Build Process

ASL provides limited support for polymorphic types. Consequently, implementing
the full RISC-V ISA manually is difficult due to the significant amount of
redundant logic required for instruction execution. To address this, we use
#link("https://jinja.palletsprojects.com/en/stable/")[Jinja] to define and
reuse code snippets.

The script `scripts/buildgen.py` acts as the primary build entry point. It
scans all source files and generates a #link("https://ninja-build.org/")[Ninja]
build file to perform the following tasks:

+ Process all `.j2` templates using JSON data from `data_files` to generate the
  final ASL code.
+ Combine the generated ASL code with the handwritten ASL code, then use the
  `asl2c` tool to compile them into C source code.
+ Compile the resulting C code alongside the interface code in `csrc/`, linking
  them with the `softfloat` library to produce the final dynamic library
  (`libpokedex_model.so`).

Now, we will step through the build process to explain the ASL model structure.

== How we "decode"

Fundamentally, an instruction can be viewed as a sequence of bytes representing
a "command" (opcode) paired with "arguments" (operands). The *decoder's*
responsibility is to interpret this sequence: it identifies which "command" to
execute and extracts the necessary "arguments", ensuring strict adherence to
the ISA specification.

=== Instructions <asl-instruction-decode>

Instruction encodings are defined in the `data_files/inst_encoding.json` file,
which is generated by `scripts/datagen.py`. Each instruction entry requires the
following fields to define an encoding:

```json
{
  "name": "addi",
  "encoding": "-----------------000-----0010011",
  "extension": "rv_i"
}
```

The encoding data is derived from the
#link("https://github.com/riscv/riscv-opcodes")[riscv/riscv-opcodes] project.
We vendor the upstream parsing tool at `scripts/riscv_opcodes_util.py`,
allowing us to accept any _riscv-opcodes_ source. This enables us to easily
define encodings for unratified extensions.

The encoding field is utilized by the `templates/inst_dispatch.asl.j2`
template, which defines the instruction decoding entry point. Each encoding
string becomes a bit-pattern match arm. When an instruction matches a specific
bit pattern, the `DecodeAndExecute` function delegates execution to the
corresponding function identified by the name field. If no bit pattern matches,
an `IllegalInstruction` result is returned.

```asl
// example of code generated inst_dispatch.asl
func DecodeAndExecute(instruction : bits(32)) => Result
begin
  case instruction of
    when '-----------------000-----0010011' =>
      return Execute_ADDI(instruction);

    // ... other bit patterns and dispatcher ...

    otherwise =>
      return IllegalInstruction();
  end
end
```

In the example above, the decoder is defined as a function. A typical function
block follows this structure:

```asl
function FunctionName(argument : type)
begin
  // function body
end
```

Pattern matching is achieved using the `case...when` expression. Each `when`
keyword defines a match arm; if the pattern matches the value, the associated
statements are executed. The `otherwise` keyword acts as a "catch-all" default
arm; if no pattern matches the value, the statements defined in `otherwise`
will be executed.

```asl
// ...
  case <expression> of
    when <pattern> =>
      <statement>
    otherwise =>
      <statement>
  end
// ...
```

As the number of supported ISA extensions grows, hand-writing these pattern
matches becomes unmanageable. That's why we use Jinja to parse the list of
encodings and automatically generate this instruction dispatch logic.

Details of `Result` and `IllegalInstruction` can be read at @asl-error-handling.

=== Control and Status Register

CSR handling follows the same logic as instruction decoding: a CSR definition
file feeds an ASL Jinja template to generate the pattern matching and dispatch
code.

The data is derived from the `riscv-opcodes` project and stored in
`data_files/csr.json`. Each CSR is defined using the following JSON format:

```json
{
  "name": "fflags",
  "mode": "urw",
  "addr": 1,
  "bin_addr": "000000000001",
  "read_write": true
}
```

This JSON data is processed by the `templates/csr_dispatch.asl.j2` template to
generate CSR operations as follows:

```asl
func ReadCSR(csr : bits(12)) => CsrReadResult
begin
  case csr of
    when '000000001000' =>
      return Read_VSTART();

    // other csr

    otherwise =>
      return CsrReadIllegalInstruction();
  end
end

func WriteCSR(csr : bits(12)) => Result
begin
  case csr of
    when '000000001000' =>
      return Write_VSTART();

    // other csr

    otherwise =>
      return IllegalInstruction();
  end
end
```

Note that the `Write_XXX` function call is only generated if the CSR is defined
as writable (based on the `read_write` fields).

Details of `CsrReadResult` and `CsrReadIllegalInstruction` can be read at @asl-error-handling.

== Stepping Instructions

We have seen how instructions are decoded and executed. Next, we examine how
instructions are fetched and passed to the decoder.

The `Step` function, defined in `handwritten/step.asl`, serves as the primary
entry point for driving the model. It processes instructions sequentially, one
at a time. Below is a simplified pseudo-code representation of this function:

```asl
// pseudo example code
func Step()
begin
  if HasInterrupt() then
    return;
  end

  let instruction = FFI_instruction_fetch(PC);
  FFI_issue_instruction(instruction);
  let result = DecodeAndExecute(instruction);
  if result.is_ok then
    FFI_commit_instruction(instruction);
  else
    HandleException();
  end
end
```

We will discuss how the Foreign Function Interface (FFI) interoperates with the
model in @asl-ffi. For now, treat any function with the `FFI_` prefix as a
black box that performs the necessary external operations.

In each execution of Step, the model fetches a single instruction using the
external FFI function. The `PC` (Program Counter) is a global architectural
state variable (32-bit) representing the physical memory address of the current
instruction. This `PC` value is passed to `FFI_instruction_fetch` to retrieve
the instruction data at that specific address.

The execution flow is as follows:

+ `FFI_issue_instruction` is called to signal that an instruction is being
  issued.
+ The instruction data is passed to `DecodeAndExecute` (implemented in
  @asl-instruction-decode) to obtain the execution result.
+ If the result indicates success (no exceptions), `FFI_commit_instruction` is
  called to signal the instruction is finalized.
+ If an exception is raised, it is delegated to a separate exception handling
  function.

Note that the actual `Step` implementation handles significantly more
complexity than shown above, including Compressed Instructions (the RISC-V "C"
extension) and detailed exception logic. Please refer to `handwritten/step.asl`
for the complete source code.

== Error handling <asl-error-handling>

ASL has poor polymorphic type support, and we don't want to use the `try...catch` error handling mechanism,
so we defined a `Result` type to hold information and error for an operation:

```asl
record Result {
  is_ok : boolean;
  cause : integer{0..31};
  payload : bits(XLEN);
};

type CsrReadResult of record {
  data: bits(XLEN),
  result: Result
};
```

There are following API for creating a `Result`:
- `Exception(cause : integer{0..31}, payload : bits(N))`: return a `Result`
  with `is_ok` set to `FALSE` and `cause`, `payload` set to corrsponding arguments.
- `Retired()`: return a `Result` with `is_ok` set to `TRUE`, other fields undefined.
- `IllegalInstruction()`: return a `Result` with `is_ok` set to `FALSE`, `cause`
  set to illegal instruction error code, `payload` undefined.
- `ExceptionEcall(mode : PrivMode)`: return a `Result` with `is_ok` set to `FALSE`,
  `cause` set to machine ecall error code when argument `mode` is M mode, and `payload` undefined.
- `ExceptionEbreak(mode : PrivMode)`: return a `Result` with `is_ok` set to `FALSE`,
  `cause` set to machine breakpoint error code, and `payload` undefined.
- `CsrReadIllegalInstruction()`: return a `CsrReadResult` with `result` set to
  `IllegalInstruction()`, `data` undefined.
- `CsrReadOk(data : bits(XLEN))`: return `CsrReadResult` with `data` set to argument,
  `result` set to `OK`.

== Architecture States <asl-architecture-states>

We have established the main logic for driving the model to process instructions
sequentially. However, instructions rarely execute in isolation; they often
exhibit *data dependencies*, where the current instruction requires the
result of a previous one.

To manage this, the model requires persistent internal storage to hold these
results between steps. This collection of information is known as the
*Architectural State*.

These states are defined in file `handwritten/states.asl` and `handwritten/states_v.asl`.

=== Program Counter

The *Program Counter (PC)* is a fundamental architectural state that stores
the address of the current instruction. It is defined in `handwritten/states.asl`
as a 32-bit variable:

```asl
var __PC : bits(32);

getter PC => bits(32) begin return __PC; end

setter PC = npc : bits(32)
begin
  assert npc[0] == '0';
  __PC = npc;
end
```

To access the program counter, developers should utilize the public `PC`
interface rather than the internal `__PC` variable. The `PC` setter enforces
validity checks, ensuring that every address stored remains properly aligned
(specifically, asserting that the least significant bit is zero).

*Example:*

```asl
// Read is always OK
let result = FFI_fetch_instruction(PC);

// Write OK
PC = 0x8000_0000[31:0];
// Write Panic
PC = 0x8000_0003[31:0];
```

=== General Purpose Register

The ASL Model currently supports the *RV32I* ISA. This defines 32 General
Purpose Registers (GPRs), each 32 bits wide (`XLEN=32`). Since register `x0`
is hardwired to `0` by definition, we optimize storage by allocating only 31
registers (`x1` through `x31`) within the model.

```asl
var __GPR : array[31] of bits(32);

getter X[i : XREG_TYPE] => bits(32)
begin
  if i == 0 then
    return Zeros(32);
  else
    return __GPR[i - 1];
  end
end

setter X[i : XREG_TYPE] = value : bits(32)
begin
  if i > 0 then
    __GPR[i - 1] = value;

    // notify emulator that a write to GPR occur
    FFI_write_GPR_hook(i, value);
  end
end
```

The `__GPR` variable is an array of 31 32-bits elements. Developers should
exclusively use the public `X[i]` interface rather than accessing the `__GPR`
variable directly.

The `X[i]` interface performs three critical tasks:

- It handles the index offset (mapping logical register `x1` to array index `0`).
- It enforces the hardwired zero behavior for `x0`.
- It signals the FFI hook whenever a write operation occurs.

*Example:*

```asl
assert(X[0] == Zeros(32));

X[5] = 0x8000_0000[31:0];
assert(X[5] == '10000000000000000000000000000000');
```

== Extensions

=== `rv32_i`
==== `addi`

== FFI and the Platform <asl-ffi>
TODO

= Simulator

== Execution
== Device Bus
== Commit Events
== Configurations

= Differential Test

== Execution
== Spike Hacks
== Interfaces

= Appendix A: Coding Style
= Appendix B: ASLc Compiler Passes
= Appendix C: How to build this document
