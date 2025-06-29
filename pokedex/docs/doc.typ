#set heading(numbering: "1.")
#show heading.where(level: 1): it => {
  pagebreak(weak: true)
  it
}
#show heading: set block(above: 1.8em, below: 1.5em)
#show link: underline
#show raw.where(block: true): it => {
  set align(center)
  set block(fill: luma(240), inset: 1em, radius: 0.2em, width: 95%)
  it
}
#show raw.where(block: false): box.with(
  fill: luma(240),
  inset: (x: 0.3em, y: 0pt),
  outset: (y: 0.3em),
  radius: 0.2em,
)

#let notes(body) = {
  show quote.where(block: true): block.with(stroke: (left: 3pt + blue, rest: none), outset: 0.4em, width: 90%)
  quote(block: true, body)
}

#outline()

= Introduction

The Pokedex project is an implementation of the RISC-V `rv32imafc` instruction
set architecture (ISA). It includes a formal specification, an instruction
decoder, and an emulator designed for functional verification.

A primary goal of this project is to provide a simple, maintainable, and
flexible design. This allows developers to easily add new instructions or make
architectural changes—capabilities not easily supported by existing tools like
`riscv/sail-riscv` or Spike (`riscv/riscv-isa-sim`).

The emulator can be linked with the T1 testbench to enable full functional
verification, including the standard RVV and custom extensions.

This guide explains the ASL (ARM Specification Language) model used in
the Pokedex project. It provides the necessary information for a developer to:

- Create functional model for RISC-V.
- Serve as a reference model for RTL design.
- Add custom instruction with low effort.

== Glossary

#table(
  columns: 2,
  [*Name*], [*Notes*],
  [*pokedex*],
  [
    Our T1 testbench works with a collection of micro-architecture designs, each
    of which is named after a Pokémon. We chose the name Pokedex for this project
    to align with that theme.

    In the world of Pokémon, a Pokédex is an essential tool for understanding the
    creatures you interact with. In the same spirit, this project provides the
    tools to help us better understand, maintain, and improve our T1
    architectures.
  ],

  [*ASL*],
  [
    ARM Specification Language (ASL) is an executable language for
    writing clear, precise specifications of Instruction Set Architectures
    (ISAs).
  ],

  [*ASLi*],
  [
    The ASL interpreter (ASLi) is an implementation of ASL that can execute ASL
    specifications either in an interpreter or by compiling via C code.
  ],

  [*GPR*], [Short term for General Propose Register],
  [*CSR*], [Short term for Control and Status Register],
  [*exception*],
  [
    When this term using in describing model, it refer to an unusual condition
    occurring at run time associated with an instruction in current hart
  ],
  [*interrupt*],
  [
    When this term using in describing model, it refer to an external asynchronous
    event that may cause a hart to experience an unexpected transfer of control.
  ],
  [*trap*],
  [
    When this term using in describing model, it refer to the transfer of control,
    which cause by exception or interrupt.
  ],
)

== How to build this document

Document are written in `docs/doc.typ` file. Developer should first install
typst and pandoc in their system environment.

- Build README

```bash
make doc
```

- Build PDF

```bash
make doc-pdf
```

== Resources

- ASL Document hosted by Intel Lab: https://intellabs.github.io/asl-interpreter/asl_reading.html
- ASL specification: https://developer.arm.com/documentation/ddi0626/latest
- RISC-V ISA Specification: https://github.com/riscv/riscv-isa-manual/
- ASL Prelude Reference: https://github.com/IntelLabs/asl-interpreter/blob/master/prelude.asl
  (Can also be obtained by running command `asli --print_spec`)

== Coding Convention

To ensure consistency and readability across the project, please adhere to the
following conventions when writing ASL code.

*Use Explicit Type Declarations*

Always provide a type annotation when declaring a variable with `let` or `var`.
This practice improves code clarity and helps prevent type-related errors.

Recommended:

```asl
let i : integer = 0x1;
```

Avoid:

```asl
let i = 0x1;
```

*Avoid Deeply Nested Operations*

Instead of nesting multiple function calls or operations in a single statement,
use intermediate variables to store the results of each step. This makes the
logic easier to read, understand, and debug.

Recommended:

```asl
let a_ext : bits(32) = ZeroExtend(GPR[1]);
let not_a : bits(32) = NOT(a_ext);
```

Avoid:

```asl
let not_a : bits(32) = NOT(ZeroExtend(GPR[1]));
```

*Declare architecture states with UPPERCASE*

Architecture states reads like global variable in normal code, thus we
prefer using UPPERCASE for the states variable name.

Recommended:

```asl
var MPP : bits(2)
```

Avoid:

```asl
var mpp : bits(2)
```


*Pad Literal Bit Vectors for Clarity*

In the ASL specification, single quotes are used to define literal bit vectors
(e.g., `'1'`). However, this syntax can be confusing for developers familiar
with other languages, where single quotes often denote a character or string.

To improve code clarity and avoid ambiguity, we recommend zero-padding all
literal bit vectors to a minimum width of 4 bits.

Recommended:

```asl
GPR[rd] = ZeroExtend('0001');
```

Avoid:

```asl
GPR[rd] = ZeroExtend('1');
```

*Declare local variable with lower_case*

Function local variable should be declared in "lower_case".

Recommended:

```asl
func a(rs1 : bits(5))
begin
  let shift_amount : integer = UInt(X[rs1]);
end
```

Avoid:

```asl
func a(rs1 : bits(5))
begin
  let shiftAmount : integer = UInt(X[rs1]);
  let ShiftAmount : integer = UInt(X[rs1]);
  let SHIFT_AMOUNT : integer = UInt(X[rs1]);
end
```

*Use double underscore to indicate private value*

Function, value binding that should only be used in current file should be prefixed
with double underscore.

Recommended:

```asl
val __MyPrivateValue : bits(32);

func __HandleMyPrivateValue()
begin
end
```

= Instruction Sets
This model implements the RISC-V instruction set architecture based on the
official `riscv-isa-manual` repository. Our implementation specifically adheres
to the latest ratified version released on May 08, 2025.

For reference, developers can download the official specification document for
from the following link:

- *RISC-V ISA Specification (Release 2025-05-08)*:
  https://github.com/riscv/riscv-isa-manual/releases/tag/20250508

This section contains basic information of how to describe instruction semantics.

== Instruction file convention
The implementation logic for each instruction is defined in its own `.asl` file.
These files are organized into directories, with each directory corresponding
to a specific RISC-V instruction set.

The directory structure for instruction sets must follow the official
`riscv-opcodes` repository. Each instruction set is represented by a directory.
And the directory's name must exactly match the corresponding extension
filename found within the `extensions/` directory of the `riscv-opcodes`
repository.

Within each directory, the filename for an instruction must strictly follow the
`<instruction_name>.asl` format:

- The `<instruction_name>` must be the lowercase version of the instruction.
- Any dot (`.`) in an instruction's name must be replaced with an underscore (`_`).

Finally, to mirror the layout of the official `riscv-opcodes` repository, all of
the previously mentioned instruction set directories (`rv_i`, `rv_v`, etc.) must be
placed inside a single top-level directory named `extensions`.

*Example*

Given the instructions `slli`, `addi` and `vle64.v`, the resulting directory and
file structure would be:

```
model/
└── extensions/
    ├── rv_i/
    │   └── addi.asl
    ├── rv_v/
    │   └── vle64_v.asl
    └── rv32_i/
        └── slli.asl
```

== Writing Instruction Semantics
The logic for each instruction is written in its own `.asl` file (e.g.,
`addi.asl`). This file contains *only the body* of the instruction's execution
logic. The `codegen` CLI tool automatically wraps this logic in a full function
signature and adds a call to it from a global dispatcher. Developer *should not*
write the function signature yourself.

*How It Works*

The `codegen` CLI tool processes every `.asl` file within the `extensions/`
directory and performs two main actions:

*1. Generates an `Execute_<INSTRUCTION_NAME>` Function*: It creates a unique
function for each instruction. The name is derived from the filename (e.g.,
`vle64_v.asl` becomes `Execute_VLE64_V`), and your code snippet is inserted
into its body. This function will always receive the 32-bit instruction opcode
as an argument:

```asl
func Execute_<INSTRUCTION_NAME>(instruction: bits(32))
```

*2. Creates a Dispatch Case*: It adds a pattern match case to the global
`Execute()` function. This dispatcher inspects the opcode of every incoming
instruction and calls the corresponding `Execute_<INSTRUCTION_NAME>` function.

*Key Responsibilities*

- *Implement Core Logic*: Your code must decode operands from the instruction
  argument, perform the operation, and write the results to the appropriate
  registers (GPRs, CSRs, etc.).
- *Update the Program Counter (PC)*: The main `Step()` function of the model does
  not automatically increment the PC. Your instruction logic is responsible for
  updating the PC value after execution (e.g., `PC = PC + 4;`). Forgetting this
  step will cause the model to loop on the same instruction.


*Example: implementing the `addi` instruction*

Here is a complete walkthrough for implementing the addi instruction.

*Step 1: Create the Instruction File*

First, create the `addi.asl` file and place it in the correct directory
according to the convention: `extensions/rv_i/addi.asl`.

*Step 2: Write the Implementation Logic*

Inside `addi.asl`, write the code to perform the "add immediate" operation.

#notes[Note there is no surrounding `func` block.]

```asl
// Decode operands from the instruction bits.
// Read following arg luts sections for details about `GetArg_*` API.
let imm : bits(11) = GetArg_ZIMM11(instruction);
let rs1 : integer = UInt(GetArg_RS1(instruction));
let rd  : integer = UInt(GetArg_RD(instruction));

// Perform the action
let rs1_val : bits(32) = GPR[rs1];
let imm_ext : bits(32) = SignExtend(imm, 32);

GPR[rd] = rs1_val + imm_ext;

// Update the Program Counter
PC = PC + 4;
```

*Step 3: Review the Generated Code*

After running the `codegen` tool, the `addi.asl` snippet will be integrated
into the model. The final generated code will look like this:

```asl
// the code generated for addi
func Execute_ADDI(instruction : bits(32))
begin
  // code from addi.asl will be inserted here

  // Decode operands from the instruction bits
  let imm : bits(11) = GetArg_ZIMM11(instruction);
  let rs1 : integer = UInt(GetArg_RS1(instruction));
  let rd  : integer = UInt(GetArg_RD(instruction));

  // Perform the action
  let rs1_val : bits(32) = GPR[rs1];
  let imm_ext : bits(32) = SignExtend(imm, 32);

  GPR[rd] = rs1_val + imm_ext;

  // Update the Program Counter
  PC = PC + 4;
end

// The global dispatcher function, now with a branch for addi
func Execute(instruction: bits(32))
begin
  case instruction of
    when 'xxxx xxxx xxxx xxxx x000 xxxx x010 0011' => // ADDI opcode
      Execute_ADDI(instruction);
    // ...
    otherwise =>
      ThrowException(IllegalInstruction);
  end
end
```

== Arg Luts

To reduce decoding bugs and simplify development, the codegen-cli tool
automatically generates helper functions for extracting argument fields from an
instruction's opcode.

This process is driven by the `arg_luts.csv` file from the official
`riscv-opcodes` repository, which contains a lookup table specifying the name
and bit position for each instruction argument.

The generated APIs follow a consistent naming and signature convention:

- Naming: All functions start with a `GetArg_` prefix, followed by the field name
  in uppercase (e.g., `GetArg_RD`, `GetArg_RS1`).
- Signature: Each function accepts the 32-bit instruction as a `bits(32)`
  parameter and returns a bit vector whose size is determined by the field's
  definition in the lookup table.

For example, instead of manually slicing the immediate field from an I-type
instruction, a developer can use the generated `GetArg_ZIMM12` API. This
function takes the 32-bits instruction and returns the corresponding 12-bit
immediate value.

```asl
func GetArg_ZIMM12(instruction : bits(32) => bits(12);
```

== CSR
This section contains basic information of how to implement Control and Status
Register (CSR) to models.

=== Reading and Writing CSRs
All CSRs operations are handled by two main APIs that are generated by the
`codegen` CLI tool.

- Read API: `func ReadCSR(csr_number: bits(12)) => bits(xlen)`
- Write API: `func WriteCSR(csr_number: bits(12), value: bits(xlen))`

The generated code works as a dispatcher. It uses pattern matching on the
`csr_number` parameter to identify the target CSR and then calls the
corresponding handler function that you have implemented for that specific
register.

For example, the generated code will look similar to this:

```asl
func ReadCSR(csr_number: bits(12)) => bits(32)
begin
  case csr_number of
    when '001_100_000_000' =>
      return Read_MSTATUS();
    when '001_100_000_001' =>
      return Read_MISA();
    // ...
    otherwise =>
      ThrowException(ExceptionType);
  end
end

func WriteCSR(csr_number: bits(12), value: bits(xlen))
begin
  case csr_number of
    when '001_100_000_000' =>
      Write_MSTATUS(value);
    when '001_100_000_001' =>
      Write_MISA(value);
    otherwise =>
      ThrowException(ExceptionType);
  end
end
```

=== Implementing CSR Handlers
While the `codegen` CLI tool creates the main dispatcher and the function
signatures, developers are responsible for implementing the specific read and
write logic for each CSR.

This is done by providing the body of the function in a dedicated `.asl` file.
The build process then combines the provided logic with the generated function
signature.

Here is the step-by-step process to add a new CSR or modify an existing one.

*Step 1: Create the Handler Files*

The logic for each CSR operation lives in its own file. The `codegen` CLI tool
looks for these files in two directories:

- `csr/read/` for read operations.
- `csr/write/` for write operations.

The `codegen` CLI tool identifies each CSR and its corresponding address
directly from the `.asl` filename. This file-based mapping requires a strict
naming convention.

The required format is: `<csr_name>_<address>.asl`

- `<csr_name>`: The name of the CSR in lowercase (e.g., `mcycle`).
- `_`: An underscore used as a separator.
- `<address>`: The CSR address represented as a lowercase hexadecimal string,
  *without the `0x` prefix* (e.g., `b00` for address `0xB00`).

For example, to implement the `mcycle` CSR, you would create files named
`csr/read/mcycle_b00.asl` and `csr/write/mcycle_b00.asl`.

*Step 2: Implement the Handler Logic*

You only need to write the code that goes inside the function body. The tool
generates the function signature around your code.

*Read Handlers:*
The tool generates a read function named `Read_<CSR_NAME>`. Your `.asl` file in
the `csr/read/` directory must contain the logic to return the CSR's value as
`bits(xlen)` type.

*Write Handlers:*
The tool generates a write function named `Write_<CSR_NAME>`. Your `.asl` file in
the `csr/write/` directory must contain the logic to handle the write
operation. The new value is passed in as the `value` argument with `XLEN` bits.

==== Example: Implementing `misa`

Let's walk through the complete process for adding the `misa` (at `0x301`) CSR.

*1. Create the File Structure*

Create the empty `misa_301.asl` files in the correct directories. Your file
structure should look like this:
```
model/
└── csr/
    ├── csrs.csv
    ├── read/
    │   └── misa_301.asl
    └── write/
        └── misa_301.asl
```

*2. Implement the Read Logic (`read/misa_301.asl`)*

Add the CSR read logic to `csr/read/misa_301.asl`. This code snippet will become
the body of the `Read_MISA()` function.
```asl
// This logic assumes only rv32i is supported. The I-bit is controlled
// by the 'misa_i' register, and all other non-MXL bits are read-only-zero.

// Reports XLEN is 32
let mxl : bits(2) = '01';

// RO zero (4 bits) + Z to I (17 bits)
let ro_zero : bits(21) = Zeros(21);

// Assuming 'misa_i' is a global register defined elsewhere.
return [mxl, ro_zero, misa_i, '000', NOT(misa_i), '000'];
```

*3. Implement the Write Logic (`write/misa_301.asl`)*

Add the corresponding logic to `csr/write/misa_301.asl`. This becomes the body of
the `Write_MISA(value: bits(32))` function.
```asl
// The 'I' bit (bit 8) of MISA is the only writable bit in this example.
misa_i = value[8];
return TRUE;
```

*4. Final Generated Code*

After running `codegen` CLI, the tool will take your `.asl` snippets and
produce the following complete, callable functions:

```asl
func Read_MISA() => bits(32)
begin
  // This logic assumes only rv32i is supported. The I-bit is controlled
  // by the 'misa_i' register, and all other non-MXL bits are read-only-zero.

  // Reports XLEN is 32
  let mxl : bits(2) = '01';

  // RO zero (4 bits) + Z to I (17 bits)
  let ro_zero : bits(21) = Zeros(21);

  // Assuming 'misa_i' is a global register defined elsewhere.
  return [mxl, ro_zero, misa_i, '000', NOT(misa_i), '000'];
end

func Write_MISA(value : bits(32)) => boolean
begin
    // The 'I' bit (bit 8) of MISA is the only writable bit in this example.
    misa_i = value[8];
    return TRUE;
end
```

== Exception

Reading resources: unprivilege spec Ch1.6

Exception is handled separately in each instruction.
There are CSRs as global variables available to signal a trap should be handled.
Details of these variables can be found at chapter
#link(<architecture-states-csr>, "Architecture States - CSRs") .

We will use the `causes.csv` file defined in riscv-opcodes repository to do
codegen for generate a list of cause number and name binding.
The `causes` definition in `riscv-opcodes` repository is a "number, description" mapping.
For each cause defined, developers will have following constants available:

```scala
val suffix = description.replace(" ", "_").toUpperCase
s"let CAUSE_${suffix} : integer = ${number};"
```

Besides ordinary CSR registers, we also maintain an extra bit to indicate
the model that there is an exception needs to be handle. Details can be view
in Chapter Architecture States.

= Architecture States

All architectural states for current ISA model, from general-purpose registers
to Control and Status Registers, are defined in the `states.asl` file. To
optimize the model, we only define the specific bits necessary for the
supported ISA features.

This section serves as a reference for all architectural states maintained by
the model.

== General Propose Register (GPRs)

This model supports the `I` extension, providing 32 general-purpose registers (`x0`
through `x31`). Because the `x0` register is a special case (hardwired to zero),
our implementation only declares a 31-element array to store the state for
registers `x1` through `x31`.

```asl
// Internal General Propose Register
var __GPR : array[31] of bits(32)
```

The `__GPR` variable is an internal architecture states. Access to the GPRs is
managed by a global array-like variable `X`. This provide a clean interface
using ASL's getter and setter keyword, allows developers to use standard array
indexing (`X[i]`) while the underlying logic handles the special case of `x0`.

```asl
getter X[i : integer{0..31}] => bits(32)
begin
  if i == 0 then
    // Always return a 32-bit zero vector for X[0]
    return Zeros(32);
  else
    // Adjust index to access the correct element for GPRs 1-31
    return __GPR[i - 1];
end

setter X[i : integer{0..31}] = value : bit(32)
begin
  // Only perform a write if the destination is not X[0]
  if i > 0 then
    __GPR[i - 1] = value;

  // Writes to GPR[0] are silently discarded
end
```

When `X[0]` is read, the getter intercepts the call and returns a 32-bit zero
vector, without accessing the `__GPR` array. A write to `x0` is silently
ignored by the setter logic, preserving its hardwired-zero behavior.

When any other register (`X[1]`-`X[31]`) is accessed, the getter adjusts the
index by `-1` and returns the corresponding value from the `__GPR` array.
A write to any register from `x1` through `x31` updates its value in the `__GPR`
array.

All access to `X` has a integer constraint check declare by `integer{0..31}`,
which allows only integer from 0 to 31. This constraints are checked by ASLi when
`--check-constraints` flag is provided.

== Control and Status Register (CSRs) <architecture-states-csr>

This sections contains CSRs behavior in current model.
If a CSR address not contains in this section get read or write,
the dispatcher will raise illegal instruction exception.

=== Read only zeros CSRs

Registers covered in this section are always read-only zero. Any write to these
registers is a no-op and will not have any side effects. Also no architecture states
will be allocated for these CSRs.

- `mvendorid`
- `marchid`
- `mimpid`
- `mhartid`
- `mconfigptr`

=== Machine ISA (misa) Register

Switching extension supports at runtime is not supported by this model. The
`misa` register is implemented as a read-only CSR register. A read to `misa`
register always return a static bit vector indicating current enabled
extensions. Any writes to `misa` register will not change value or have any
side effects.

```asl
let misa : bits(32) = [
  // MXLEN 32
  '01',
  Zeros(4),
  // Z-N
  Zeros(13),
  // M
  '1',
  // LJKI
  '0001',
  // HGFE
  '0010',
  // DCBA
  '0101'
];

return misa;
```

Our implementation will now return support for `rv32imafc`, 'x' is not enabled for now.

Since `misa` is a read-only value, no states will be allocated in current model.

=== Machine status (mstatus) Register

Current model focus on machine mode only, supervisor and user mode are not implemented.
So for `mstatus` register, model only required following fields in `mstatus` register:

- `mie`: global interrupt-enable bit for machine mode
- `mpie`: the interrupt-enable bit active prior to the trap
- `mpp[1:0]`: previous privilege level

The `mstatush` register is also not required if we only implement M-mode.

The above bit fields will be declare as individual register in `states.asl` file.

```asl
var MSTATUS_MIE  : bit
var MSTATUS_MPIE : bit
var MSTATUS_MPP  : bits(2)
```

An example read/write operation to `mstatus` register looks like following:

```asl
func Read_MSTATUS()
begin
  return [Zeros(18), MSTATUS_MPP, '000', MSTATUS_MPIE, '000', MSTATUS_MIE, '000'];
end

func Write_MSTATUS(value : bits(32))
begin
  MSTATUS_MIE = value[3];
  MSTATUS_MPIE = value[7];
  MSTATUS_MPP = value[12:11];
end
```

=== Machine Cause (mcause) Register

The `mcause` register is store in two states: a `Interrupt` bit register
and a 31 bits length exception code register:

```asl
var MCAUSE_INTERRUPT : bit
var MCAUSE_EXCEPTION_CODE : bits(31)
```

A read to the `mcause` CSR register will have a concatenated 32-bit value
from above register, with the interrupt bit at top, exception code value
at bottom.

```asl
function Read_MCAUSE()
begin
  return [MCAUSE_INTERRUPT, MCAUSE_EXCEPTION_CODE];
end
```

A write to the `mcause` CSR register will only write to the
`MCAUSE_EXCEPTION_CODE` register.

```asl
function Write_MCAUSE(value : bits(32))
begin
  MCAUSE_EXCEPTION_CODE = value[31:0];
end
```

=== Machine Interrupt (mip and mie) Registers

=== Machine Trap Vector (mtvec) Register

Register `mtvec` holds address to the trap handler.
It is implemented with two states:

- `MTVEC_MODE`: two bits size bit vector holds the least significant two bits `MTVEC[1:0]`;
- `MTVEC_BASE`: 28 bits size bit vector holds the base address;

In current implementation, `MTVEC_MODE` only store bits represent 0 or 1. For a
write with `value[1:0]` larger or equals to 2, the previous `mtvec` mode value
will be preserved.

```asl
var mode : bits(2);
case value[1:0] of
  when '00' => mode = '00';
  when '01' => mode = '01';
  otherwise => mode = MTVEC_MODE;
end

MTVEC_MODE = mode;
```

In current implementation, we always use round down strategy to align the base address.
And since the last two bits of base address is zero, the `MTVEC_BASE` variable only
stores 28 bits, and will be concatenated with two zero bits when read.

```asl
// value[3:2] is trimmed
MTVEC_BASE = value[31:4];
```

A read to `mtvec` is simply concatenate all the bits:

```asl
return [MTVEC_BASE, '00', MTVEC_MODE];
```

= Rust Simulator

The ASL model itself is designed as a passive library. Its sole responsibility
is to define the RISC-V instruction set architecture, including instruction
semantics and architecture states. The ASL code does not contain an entry point
(main function), an event loop, or memory management. It only describes the
core logic of the processor.

The execution environment is provided by a simulator written in Rust. This
component acts as the driver for the ASL model and is responsible for all
runtime operations, including loading executable file into memory, maintaining
memory state, driving the model to consume instructions and recording each
change happens in model architecture states.

== File Structures

```txt
model/simulator/
    ├── difftest
    │   ├── assets
    │   ├── src
    │   │   ├── main.rs            -- difftest CLI entry
    │   │   ├── pokedex.rs         -- parser for pokedex simulator event
    │   │   └── spike.rs           -- parser for spike event
    │   └── Cargo.toml
    ├── pokedex
    │   ├── src
    │   │   ├── bin
    │   │   │   └── pokedex.rs     -- pokedex CLI entry
    │   │   ├── ffi.rs             -- FFI functions implementation
    │   │   ├── lib.rs
    │   │   ├── model.rs           -- ASL model function and variable wrapper
    │   │   └── simulator.rs       -- Simulator logic and states
    │   ├── asl_export.h           -- wrapper for ASL exported header
    │   ├── build.rs
    │   └── Cargo.toml
    ├── Cargo.lock
    └── Cargo.toml
```

== Development Setup

To correctly configure rust-analyzer and export the necessary environment
variables for the project, run the following command:

```bash
nix develop '.#pokedex.simulator.dev'
```

== Simulator States

We maintain following states at Rust side

```rust
pub struct Simulator {
    pc: u32,
    memory: Vec<u8>,
    exception: Option<SimulationException>,

    last_instruction: u32,
    last_instruction_met_count: u8,
    max_same_instruction: u8,

    statistic: Statistic,
}
```

The `pc` field holds the address of the instruction currently being executed. Its
primary purpose is to ensure accurate logging for differential testing against
Spike.

In our ASL model, the program counter is updated immediately during an
instruction's execution; there is no `nextPC` state. This architectural choice
creates a logging challenge for instructions like `jalr`, which both modify a
register and jump to a new address.

If we logged the register write after `PC` was updated, the log would
incorrectly show the write occurring at the jump *destination* address, not at
the address of the `jalr` instruction itself. This would cause a mismatch when
comparing our execution trace with Spike's.

To solve this, the simulator's `pc` field is updated after the model executes
the instruction. This field provides a stable record of the instruction's own
address. When logging state changes, such as a register write, we use this
value to ensure the log entry is correctly associated with the instruction that
caused it, guaranteeing an accurate comparison with the reference log from
Spike.

The `memory` field is a simple, large, byte indexed array. It will be read and
written by corresponding FFI functions from model, it will also be written when
first loading executable file into memory.

The simulator includes an `exception` field designed to capture events that occur
within the guest executable and are caught by the host simulator. This field
is used for recoverable or expected events originating from the code being
simulated, including infinite loop, executable notify a power off signal...etc.

#notes[It is critical to distinguish between exceptions that occur within the guest
  executable versus runtime errors that originate in the simulator itself. For
  example, an unaligned read/write should be treat as guest exception. However a
  memory operation requested with an address size that cannot fit into unsigned
  32-bit integer represents a fundamental flaw and should be immediately rejected
  with fatal error, not considered recoverable.]

To avoid spamming the terminal during an infinite exception loop—a common issue
for those familiar with Spike—this simulator includes a mechanism to detect and
halt prolonged instruction repetition. This feature helps diagnose potential
infinite loops by tracking if the same instruction is executed too many times
consecutively. It is controlled by the following fields:

- `last_instruction`: Stores the opcode of the most recently executed instruction.
- `last_instruction_met_count`: A counter that increments each time the current
  instruction's opcode matches last_instruction. It resets if a different
  instruction is executed.
- `max_same_instruction`: A configurable limit. If `last_instruction_met_count`
  exceeds this value, the simulator will halt execution and report an infinite
  loop exception.

The `statistic` field contains counters to record runtime information that will
be reported after simulation end.

== FFI functions

Following are FFI functions required from ASL model that developers should
implemented on Rust side. These function are explicitly declare at
`external.asl` file.

#notes[Due to monomorphic optimization, generated C functions will be suffix
  with unique ID and data size. Developers need to make sure that a correct
  function symbol is declared. They can check final function signatures at
  `*_types.h` header file after ASLi generated C files.]

- Memory R/W APIs
  - `void FFI_write_physical_memory_{8,16,32}bits_0(uint32_t addr, uint{8,16,32}_t data)`
  - `uint{8,16,32}_t FFI_read_physical_memory_{8,16,32}bits_0(uint32_t addr)`
  - `uint32_t FFI_instruction_fetch_0(uint32_t pc)`

All the FFI read/write memory APIs with "physical" infix operates on memory directly.
This means there is no memory protection, and memory violation will immediately raise
fatal error at host emulator. Unaligned read/write should be manually detect at
model side. And out-of-memory write is OK to break the host emulator.

- Hook APIs
  - `void FFI_write_GPR_hook_0(signed _BitInt(6) reg_idx, uint32_t data)`
  - `bool FFI_is_reset_0()`
  - `void FFI_emulator_do_fence_0()`
  - `void FFI_ebreak_0()`
  - `void FFI_ecall_0()`

TODO: explain debug message format

- Debug APIs
  - `void FFI_print_string_0(const char* s)`
  - `void FFI_print_bits_hex_0(uint32_t d)`

Current implementation can be found in `simulator/simulator/pokedex/src/ffi.rs`.

= Differential Tests

= Build Instruction

== Debug generated code
Developers can run following commands to get full source code prepare for ASLi to compile.

```txt
$ cd pokedex/model
$ nix develop ".#pokedex.sim-lib" -c make project
$ ls ./build/1-rvcore
arg_lut.asl  asl2c.prj  csr_op.asl  execute.asl  external.asl  project.json  states.asl  step.asl
```

The `project` target will run `codegen` CLI and copy manually implemented
architecture states ASL implementation into one folder, with the `asl2c.prj`
lowering script and `project.json` configuration.

File `asl2c.prj` contains a list of optimization and lowering pass ASLi needs to run.
Lowering pass used in `asl2c.prj` is documented at chapter #link(<appendix-a>, "Appendix A").

#notes[Note that the default optimization `:xform_constprop` is not enabled, details at
  #link(
    "https://github.com/IntelLabs/asl-interpreter/issues/105",
    [`IntelLab/asl-interpreter issue#105`],
  )]

File `project.json` record all the functions used for FFI.
The `imports` field records unimplemented function that needs to be linked from other sources.
The `exports` field records all the functions that is required by outside library.

Developers can run following commands to get ASLi generated C code:

```txt
$ cd pokedex/model
$ nix develop ".#pokedex.sim-lib" -c make asl2c
$ ls ./build/2-cgen
dumps/  pokedex-sim_exceptions.c  pokedex-sim_exceptions.h  pokedex-sim_funs.c  pokedex-sim_types.h  pokedex-sim_vars.c  pokedex-sim_vars.h
```

The `dumps/` directory collect ASL code dumps after each lowering pass, developers can debug the
optimization by inspect those dump file.

== Get archived ASL library

Developers can get the archive file with following commands

```console
$ cd pokedex/model
$ nix develop ".#pokedex.sim-lib" -c make install
$ ls ./build/simlib
include/  lib/
```

Generated headers are placed under the `include/` directory, and the ASL model code is packaged under `lib/` directory.

= Appendix A: `ASLi` commands for lowering <appendix-a>

== Filter unreachable code from exports

This command discard any code not reachable from the list of exported functions.

```asl
:filter_reachable_from --keep-builtins exports
```

Remove `--keep-builtins` flag to remove unreachable prelude functions.
Be aware the removal of built-ins functions better to be used at the end of the pass pipeline to avoid
missing functions after optimization.

== Eliminate `typedef`

```asl
:xform_named_type
```

== Eliminate bit and int arithmetic operation

Eliminate bit,int arithmetic operations like "'000' + 3".

```asl
:xform_desugar
```

== Eliminate bit vector concatenate

Eliminate bit-tuples like "[x,y] = z;" and "x[7:0, 15:8]".

```asl
:xform_bittuples
```

== Convert bit-slice operation

Convert all bit-slice operations to use the +: syntax, e.g., "x[7:0]" --> "x[0 +: 8]".

```asl
:xform_lower
```

== Eliminate slices of integers

Eliminate slices of integers by first converting the integer to a bitvector.
E.g., if `x` is of `integer` type, then convert `x[1 +: 8]` to `cvt_int_bits(x, 9)[1 +: 8]`

```asl
:xform_int_bitslices
```

== Convert getter/setter

Convert use of getter/setter syntax to function calls.
E.g., `Mem[a, sz] = x` --> `Mem_set(a, sz, x)`

```asl
:xform_getset
```

== TODO

```asl
:xform_valid track-valid
```

== Constant propagation

Perform constant propagation without unrolling loops.
This helps identify potential targets for the monomorphicalize pass.

```asl
:xform_constprop --nounroll
```

== Monomorphicalize functions

Create specialized versions of every bitwidth-polymorphic function and change
all function calls to use the appropriate specialized version. (Note that this
performs an additional round of constant propagation.)

```asl
:xform_monomorphize --auto-case-split
```

== Lift let-expressions

Lift let-expressions as high as possible out of an expression
e.g., `F(G(let t = 1 in H(t))) -> let t = 1 in F(G(H(t)))`.

(This makes later transformations work better if, for example,
they should match against `G(H(..))`)

Note that source code is not expected to contain let-expressions.
They only exist to make some transformations easier to write.

```asl
:xform_hoist_lets
```

== Convert bit vector slice operation to bit operation

Convert bitslice operations like "x[i] = '1';" to a combination
of AND/OR and shift operations like "x = x OR (1 << i);"
This works better after constant propagation/monomorphization.

```
:xform_bitslices
```

Add flag `--notransform` when using `ac`, `sc` backend.

== Convert match to if

Any case statement that does not correspond to what the C language supports is
converted to an if statement.
This works better after constant propagation/monomorphization because that can
eliminate/simplify guards on the clauses of the case statement.

```asl
:xform_case
```

== Wrap variable

```asl
:xform_wrap
```

== Create bounded int

```asl
:xform_bounded
```

== Filter function not listed in imports

The `imports` field should be defined in configuration in following form:

```json
{
  "imports": [
    "TraceMemRead",
    "TraceMemWrite"
  ]
}
```

```asl
:filter_unlisted_functions imports
```

== Check monomorphic

Check that all definitions are bitwidth-monomorphic and report a useful error
message if they are not. The code generator will produce an error message if it
finds a call to a polymorphic functions but we can produce a much more useful
error message if we scan for all polymorphic functions and organize the list of
functions into a call tree so that you can see which functions are at the roots
of the tree (and therefore are the ones that you need to fix).

```asl
:check_monomorphization --fatal --verbose
```
