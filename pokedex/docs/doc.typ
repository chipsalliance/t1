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

#notes[
  *Why using Pokedex as project name*

  Our T1 testbench works with a collection of micro-architecture designs, each
  of which is named after a Pokémon. We chose the name Pokedex for this project
  to align with that theme.

  In the world of Pokémon, a Pokédex is an essential tool for understanding the
  creatures you interact with. In the same spirit, this project provides the
  tools to help us better understand, maintain, and improve our T1
  architectures.
]

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

== Compatibility

Note that we are using Intel Labs fork of ASL Interpreter, which doesn't
strictly implementing the ASL

= Overview
This project provides a simulator for a custom RISC-V Instruction Set
Architecture (ISA) model. The simulator can load an ELF file, execute its
instructions, and log all architectural state changes, such as register and CSR
modifications.

Its primary goal is to serve as a "source of truth" for the ISA's behavior,
enabling co-simulation to identify bugs in Register Transfer Level (RTL)
designs.

Following is an overview of the project architecture:

#align(center, image("architecture.drawio.png", width: 85%))

The system is composed of two primary components: an *ASL Model* that defines
the ISA and a *Rust Simulator* that executes it.

== ASL Model

We use ARM's Architecture Specification Language (ASL) to formally describe the
RISC-V ISA. The core logic is organized into three categories within the
`model/` directory:

- *`csr/`*: Contains code snippets for individual Control and Status Register (CSR) implementations.
- *`extensions/`*: Holds code snippets defining the semantics for each instruction, organized by ISA extension.
- *`handwritten/`*: Includes foundational, manually written code, such as architectural state declarations and helper libraries.

To improve accuracy and reduce manual effort, significant parts of the model
are code-generated based on the official `riscv-opcodes` repository. This
includes instruction decoders, dispatch logic, and CSR read/write dispatchers.

== Rust Simulator

The ASL model defines *what* the ISA does but not *how* to run it. The
simulation environment is provided by a platform written in Rust. It links to
the compiled ASL model and handles all runtime responsibilities that the model
does not, including:

- Command-line argument parsing
- Memory allocation and maintenance
- Logging utilities
- Interrupt handling
- Driving the simulation loop

The simulator's entry point is `simulator/pokedex/src/bin/pokedex.rs`, which
parses command-line arguments and runs the main simulation loop.

The simulator logic is organized into several key modules:

- *`simulator.rs`*: The core simulation driver, responsible for managing memory and interrupts.
- *`ffi.rs`*: Exposes Rust functions (like memory access) to the ASL model through a C-compatible API.
- *`model.rs`*: A wrapper around the C code that is auto-generated from the ASL model by `bindgen`.

The ASL model communicates with the Rust simulator through a Foreign Function
Interface (FFI). The ASL code is first compiled into a C archive
(`libpokedex_sim.a`) with corresponding header files. The Rust simulator then
uses these C-bindings to step each instruction and handle I/O operations
like memory loads and stores.

The `build.rs` script orchestrates the build process. It read the ASLi
generated C code and then uses the `bindgen` tool to generate Rust bindings
from the `asl_export.h` header file. These bindings are then used by the
`model.rs` module to interact with the ISA model.

Implementation details can be found at chapter @rust-simulator.

== Differential Testing with Spike

To ensure our model's correctness, we perform differential testing against
`riscv-isa-sim` (Spike), the official RISC-V golden model. By comparing our
model's architectural state changes against Spike's on a per-instruction basis,
we can verify that our implementation is trustworthy and accurate.

The `difftest` CLI will read a configuration where user specify the directory
for all test cases and arguments for simulator and spike. It will run simulator
and spike automatically, then get corresponding commit log and parse to structure
metadata for comparing. When any part of the log is mismatched, like missing
register read/write operation or register get written with different value at same
point, the `difftest` CLI will run fail and provide dump near the error place.

#notes[By default, differential testing for memory operations is disabled.

  This is because the behavior of the memory system is platform-specific and
  not defined by the ISA specification, making a direct comparison between
  different simulators impractical for these operations.]


= Exception

== Exception API

To handle operations that may fail, our ASL model emulates Rust's `Result`
type. Since ASL does not support generic enums, we use a custom `record` and a
set of helper functions to provide a standardized way of returning either a
successful value or an exception.

```asl
// A record to hold the outcome of an operation.
record Result {
  cause : integer;
  value : bits(32);
  is_ok : boolean;
};

// Helper functions to create a Result.
func OK(value : bits(32)) => Result
func Exception(cause : integer, trap_value : bits(32)) => Result
func Retired() => Result
```

- *`OK(value)`*: Use this to return a successful result. It creates a
  `Result` with `is_ok` set to `TRUE` and the `value` field populated.
- *`Exception(cause, trap_value)`*: Use this to return a failure. It creates
  a `Result` with `is_ok` set to `FALSE`, the `cause` field set to the
  exception type, and the `trap_value` field holding relevant context about the
  error (e.g., the faulting address).
- *`Retired()`*: Use this for successful operations that do not produce a
  return value. It creates a `Result` with `is_ok` set to `TRUE`, `cause` set
  to `-1` and `value` set to zeros.

== Exception Causes
While the `Exception` function can be called with any custom `cause` ID, most
exceptions should use the standard cause codes defined by the RISC-V privilege
specification.

We auto-generate these cause codes as named constants from the
#link("https://github.com/riscv/riscv-opcodes/blob/master/causes.csv")[`causes.csv`]
file in the official `riscv-opcodes` repository. For each entry in the CSV, a
constant is generated with the `CAUSE_` prefix, followed by the uppercase
description with spaces replaced by underscores.

For example, the description "Misaligned load" becomes the constant `CAUSE_MISALIGNED_LOAD`.

*Example Usage:*

To return a misaligned load exception, you would use the generated constant like this:

```asl
// ...
// Check if the address is misaligned.
if addr[1:0] != '00' then
  // Return an exception with the standard cause and the faulting address.
  return Exception(CAUSE_MISALIGNED_LOAD, addr);
end

// ...
```

== Trap Handling

Trap handling is explained at @trap-handling.

= Instruction Sets
This model implements the RISC-V instruction set architecture based on the
official `riscv-isa-manual` repository. Our implementation specifically adheres
to the latest ratified version released on May 08, 2025.

For reference, developers can download the official specification document for
from the following link:

- *RISC-V ISA Specification (Release 2025-05-08)*:
  https://github.com/riscv/riscv-isa-manual/releases/tag/20250508

This section contains details guidance of how we describe instruction semantics.

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

=== Example

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

=== How It Works

The `codegen` CLI tool processes every `.asl` file within the `extensions/`
directory and performs two main actions:

*1. Generates an `Execute_<INSTRUCTION_NAME>` Function*: It creates a unique
function for each instruction. The name is derived from the filename (e.g.,
`vle64_v.asl` becomes `Execute_VLE64_V`), and your code snippet is inserted
into its body. This function will always receive the 32-bit instruction opcode
as an argument:

```asl
func Execute_<INSTRUCTION_NAME>(instruction: bits(32)) => Result
```

*2. Creates a Dispatch Case*: It adds a pattern match case to the global
`Execute()` function. This dispatcher inspects the opcode of every incoming
instruction and calls the corresponding `Execute_<INSTRUCTION_NAME>` function.

=== Developers Responsibilities

- *Implement Core Logic*: Your code must decode operands from the instruction
  argument, perform the operation, and write the results to the appropriate
  registers (GPRs, CSRs, etc.).
- *Update the Program Counter (PC)*: The main `Step()` function of the model does
  not automatically increment the PC. Your instruction logic is responsible for
  updating the PC value after execution (e.g., `PC = PC + 4;`). Forgetting this
  step will cause the model to loop on the same instruction.
- *Return Result*: Developer should return `Result` value after handling the execution.

=== Example: implementing the `addi` instruction in `rv_i`

Here is a complete walkthrough for implementing the addi instruction.

*Step 1: Create the Instruction File*

First, create the `addi.asl` file and place it in the correct directory
according to the convention: `extensions/rv_i/addi.asl`.

*Step 2: Write the Implementation Logic*

Inside `addi.asl`, write the code to perform the "add immediate" operation.

#notes[Note there is no surrounding `func` block.]

```asl
// Decode operands from the instruction bits.
// Read following arg luts sections for details about `Get*` API.
let rd  : integer{0..31} = UInt(GetRD(instruction));

// NOP optimization
if rd != 0 then
  let imm : bits(12) = GetIMM(instruction);
  let rs1 : integer{0..31} = UInt(GetRS1(instruction));
  X[rd] = X[rs1] + SignExtend(imm, 32);
end

PC = PC + 4;

return Retired();
```

*Step 3: Review the Generated Code*

After running the `codegen` tool, the `addi.asl` snippet will be integrated
into the model. The final generated code will look like this:

```asl
// the code generated for addi
func Execute_ADDI(instruction : bits(32)) => Result
begin
  // code from addi.asl will be inserted here

  let rd  : integer{0..31} = UInt(GetRD(instruction));

  // NOP optimization
  if rd != 0 then
    let imm : bits(12) = GetIMM(instruction);
    let rs1 : integer{0..31} = UInt(GetRS1(instruction));
    X[rd] = X[rs1] + SignExtend(imm, 32);
  end

  PC = PC + 4;

  return Retired();
end

// The global dispatcher function, now with a branch for addi
func Execute(instruction: bits(32)) => Result
begin
  case instruction of
    when 'xxxx xxxx xxxx xxxx x000 xxxx x001 0011' =>
        return Execute_ADDI(instruction);
    // ...
    otherwise =>
      return Exception(CAUSE_ILLEGAL_INSTRUCTION, instruction);
  end
end
```

== Arg Luts

To reduce decoding bugs and simplify development, extracting arg field from
instruction is abstracted to functions implemented in `handwritten/arg.asl` file.

The arg APIs follow a consistent naming and signature convention:

- Naming: All functions start with a `Get` prefix, followed by the field name
  in uppercase (e.g., `GetRD`, `GetRS1`).
- Signature: Each function accepts the 32-bit instruction as a `bits(32)`
  parameter and returns a bit vector whose size is determined by the field's
  definition in the lookup table.

For example, instead of manually slicing the immediate field for each J-type
instruction, developer should use the provided `GetJIMM` API. This function
takes the 32-bits instruction and returns the corresponding 20-bit immediate
value.

```asl
func GetJIMM(inst : bits(32)) => bits(20)
begin
  let imm20 : bit = inst[31];
  let imm10_1 : bits(10) = inst[30:21];
  let imm11 : bit = inst[20];
  let imm19_12 : bits(8) = inst[19:12];

  return [imm20, imm19_12, imm11, imm10_1];
end
```

== CSR
This section contains basic information of how to implement Control and Status
Register (CSR) to models.

=== Reading and Writing CSRs
All CSRs operations are handled by two main APIs that are generated by the
`codegen` CLI tool.

- Read API: `func ReadCSR(csr_number: bits(12)) => Result`
- Write API: `func WriteCSR(csr_number: bits(12), value: bits(32)) => Result`

The generated code works as a dispatcher. It uses pattern matching on the
`csr_number` parameter to identify the target CSR and then calls the
corresponding handler function that you have implemented for that specific
register.

For example, the generated code will look similar to this:

```asl
func ReadCSR(csr_number: bits(12)) => Result
begin
  case csr_number of
    when '1111 0001 0010' =>
      return Read_MARCHID();
    when '0011 0100 0010' =>
      return Read_MCAUSE();
    when '0011 0100 0001' =>
      return Read_MEPC();
    // ...
    otherwise =>
      return Exception(CAUSE_ILLEGAL_INSTRUCTION, ZeroExtend(csr, 32));
  end
end

func WriteCSR(csr_number: bits(12), value: bits(32)) => Result
begin
  case csr_number of
    when '1111 0001 0010' =>
      return Write_MARCHID(value);
    when '0011 0100 0010' =>
      return Write_MCAUSE(value);
    when '0011 0100 0001' =>
      return Write_MEPC(value);
    // ...
    otherwise =>
      return Exception(CAUSE_ILLEGAL_INSTRUCTION, ZeroExtend(csr, 32));
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
`Result` type.

*Write Handlers:*
The tool generates a write function named `Write_<CSR_NAME>`. Your `.asl` file in
the `csr/write/` directory must contain the logic to handle the write
operation. The new value is passed in as the `value` argument with `XLEN` bits.
For read-only CSR, a write handler must return illegal instruction exception.

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

// machine xlen is read-only 32;
let MXL : bits(2) = '01';
let MISA_EXTS : bits(26) = [
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

let misa : bits(32) = [
  MXL,
  Zeros(4),
  MISA_EXTS
];

return misa;
```

*3. Implement the Write Logic (`write/misa_301.asl`)*

Add the corresponding logic to `csr/write/misa_301.asl`. This becomes the body of
the `Write_MISA(value: bits(32))` function.

```asl
return Retired();
```

*4. Final Generated Code*

After running `codegen` CLI, the tool will take your `.asl` snippets and
produce the following complete, callable functions:

```asl
func Read_MISA() => Result
begin
  // machine xlen is read-only 32;
  let MXL : bits(2) = '01';
  let MISA_EXTS : bits(26) = [
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

  let misa : bits(32) = [
    MXL,
    Zeros(4),
    MISA_EXTS
  ];

  return OK(misa);
end

func Write_MISA(value : bits(32)) => Result
begin
  return Retired();
end
```

= Architecture States

All architectural states for current ISA model, from general-purpose registers
to Control and Status Registers, are defined in the `states.asl` file. To
optimize the model, we only define the specific bits necessary for the
supported ISA features.

This section serves as a reference for all architectural states maintained by
the model.

== Architecture State vs. CSR Implementation

The relationship between an *Architectural State* and a *Control and Status
Register (CSR)* is that of implementation versus interface.

Developer can think of *Architectural States* as the actual, physical variables
in our model. These are custom-sized registers that hold the state of the
model.

In contrast, *CSRs* are the standardized, abstract interface used to access
those underlying architectural states. Because of this separation, we only
implement the architectural state bits that are necessary for our model's
features, rather than defining storage for every bit of every CSR in the
specification.

The RISC-V specification defines several types of CSRs, such as *WPRI*
(Write-Preserve, Read-Ignore), *WLRL* (Write-Legal, Read-Legal), and *WARL*
(Write-Any, Read-Legal). These types dictate how writes are handled—for
instance, some fields in a write may be consider invalid and be ignored.

To ensure our model correctly adheres to these rules, we do not expose the raw
architectural states directly. Instead, we provide constrained getter and
setter APIs for each state. This design imposes the specification's rules at
the access layer, guaranteeing that any register write performed by your
instruction logic is automatically handled correctly according to the CSR's
type, and consider any invalid write as "model implementation bugs".

As a *Write-Any, Read-Legal (WARL)* register, the `mtvec` CSR provides a clear
example of separating the public-facing CSR from its underlying architectural
states.

The `mtvec` CSR is composed of two architectural states:
- `BASE`: A 30-bit field for the trap address.
- `MODE`: A 2-bit field for the trap mode.

The key distinction lies in how writes are handled:

1.  *CSR Write Handler (Permissive):* As a WARL register, any 32-bit value can
    be written to the `mtvec` CSR. The CSR's write logic is responsible for
    sanitizing this input. For example, the `MODE` field only supports values
    `0b00` and `0b01`. If a write contains `0b10` or `0b11` in the mode bits, the
    handler correctly ignores the update for that field, treating it as a no-op.

2.  *Architectural State (Strict):* The internal `MODE` state itself should
    *never* contain an illegal value like `0b10` or `0b11`. The sanitization
    logic in the CSR handler guarantees this. Therefore, our model's internal logic
    will use an *assertion* to validate the `MODE` state. If this assertion ever
    fails, it signals a critical bug in the CSR's write-handling or some explicit
    write logic that must be fixed.

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
which allows only integer from 0 to 31. This constraints are checked by ASLi
when `--check-constraints` flag is provided. Developer should also ensure
`--runtime-check` flag is provided to avoid invalid type cast.

== Control and Status Register (CSRs) <architecture-states-csr>

This sections contains CSRs behavior in current model.
If a CSR address not contains in this section get read or write,
the dispatcher will raise illegal instruction exception.

=== Read only zeros CSRs

Registers covered in this section are always read-only zero. Any write to these
registers will return a illegal instruction exception. Also no architecture states
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
extensions. Any writes to `misa` register will return illegal instruction
exception.

```asl
// machine xlen is read-only 32;
let MXL : bits(2) = '01';
let MISA_EXTS : bits(26) = [
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

let misa : bits(32) = [
  MXL,
  Zeros(4),
  MISA_EXTS
];

return misa;
```

Our implementation will now return support for `rv32imafc`, 'x' is not enabled for now.

Since `misa` is a read-only value, no states will be allocated in current model.

=== Machine status (mstatus) Register

Current model focus on machine mode only, supervisor and user mode are not implemented.
So for `mstatus` register, model only provide following fields for `mstatus` register:

- `mie`: global interrupt-enable bit for machine mode
- `mpie`: the interrupt-enable bit active prior to the trap
- `mpp[1:0]`: previous privilege level

The `mstatush` register is also not required since we only implement M-mode.
Any write to `mstatush` is a no-op, and read will get zeros.

The above bit fields will be declare as individual register in `states.asl` file.

```asl
var MSTATUS_MIE : bit;
var MSTATUS_MPIE : bit;
var MSTATUS_MPP : PRIVILEGE_LEVEL;
```

Variables `MSTATUS_MIE` and `MSTATUS_MPIE` are of one bit type, and there are by default
containing valid value, so no constraints added to them.

Variable `MSTATUS_MPP` only holds one valid value (M mode) but should it have two bits,
so a new enumeration type `PRIVILEGE_LEVEL` is added to limit the value.

```asl
enumeration PRIVILEGE_LEVEL {
  PRIV_MACHINE_MODE
};
```

Field `PRIV_MACHINE_MODE` will be converted to bits vector value `0b11`, and convert from
bits vector `0b11`. Any other value convert into type `PRIVILEGE_LEVEL` will be seen as
internal model bug:

```asl
func __PrivLevelToBits(priv : PRIVILEGE_LEVEL, N: integer) => bits(N)
begin
  case priv of
    when PRIV_MACHINE_MODE => return ZeroExtend('11', N);
  end
end

func __BitsToPrivLevel(value : bits(N)) => PRIVILEGE_LEVEL
begin
  let mode : integer = UInt(value);
  case mode of
    when 3 => return PRIV_MACHINE_MODE;
    otherwise => assert FALSE;
  end
end
```

An example read/write operation to `mstatus` register looks like following:

```asl
func Read_MSTATUS()
begin
  return [
    // SD[31], WPRI[30:25], SDT[24], SPELP[23], TSR[22], TW[21], TVM[20]
    // MXR[19], SUM[18], MPRV[17], XS[16:15], FS[14:13]
    Zeros(19),
    // MPP[12:11]
    MSTATUS_MPP_BITS,
    // VS[10:9], SPP
    '000',
    // MPIE
    MSTATUS_MPIE,
    // UBE, SPIE, WPRI
    '000',
    MSTATUS_MIE,
    // WPRI, SIE, WPRI
    '000'
  ];
end

func Write_MSTATUS(value : bits(32))
begin
  MSTATUS_MIE = value[3];
  MSTATUS_MPIE = value[7];

  if value[12:11] == '11' then
    MSTATUS_MPP_BITS = value[12:11];
  end

  return Retired();
end
```

=== Machine Cause (mcause) Register

The `mcause` register is store in two states: a `Interrupt` bit register
and a 31 bits length exception code register:

```asl
var MCAUSE_IS_INTERRUPT : boolean;
var MCAUSE_XCPT_CODE : bits(31);
```

A read to the `mcause` CSR register will have a concatenated 32-bit value
from above register, with the interrupt bit at top, exception code value
at bottom.

```asl
function Read_MCAUSE()
begin
  return [MCAUSE_IS_INTERRUPT_BIT, MCAUSE_EXCEPTION_CODE];
end
```

Since `mcause` CSR is a WLRL register, we don't validate the value, it is up to
software to verify the correctness of CSR value.

```asl
function Write_MCAUSE(value : bits(32))
begin
  MCAUSE_IS_INTERRUPT_BIT = value[31];
  MCAUSE_XCPT_CODE = value[30:0];

  return Retired();
end
```

=== Machine Interrupt (mip and mie) Registers

We have only M-mode support in current implementation, so LCOFIP, supervisor
interrupt bits and software interrupt bit (MSIP/MSIE) are not allocated and
are read-only zero.

External interrupt pending bit (MEIP) and Timer interrupt pending bit (MTIP)
is controlled by external controller at semantic. We use FFI functions
`FFI_machine_external_interrupt_pending` and `FFI_machine_time_interrupt_pending`
to get the value, and no states will be allocated for these two bits.
Any CSR write to `mip` will raise illegal instruction exception.

```asl
function Read_MIP() => bits(32)
begin
  var tmp : bits(32) = Zeros(32);
  tmp[7] = MTIP;
  tmp[11] = MEIP;
  return tmp;
end

func Write_MIP(value : bits(32)) => Result
begin
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end
```

External interrupt enable (MEIE) and timer interrupt enable (MTIE) are single
bit register. They contains only two value and thus no constraints will be imposed
on these bits.

```asl
var MEIE : bit;
var MTIE : bit;

func Write_MIE(value : bits(32)) => Result
begin
  MTIE = value[7];
  MEIE = value[11];

  return Retired();
end

func Read_MIE() => bits(32)
begin
  var tmp : bits(32) = Zeros(32);
  tmp[7] = MTIE;
  tmp[11] = MEIE;
  return tmp;
end
```

=== Machine Trap Vector (mtvec) Register

Register `mtvec` holds address to the trap handler.
It is implemented with two states:

- `MTVEC_MODE`: enumeration that contains only "direct" and "vectored" mode;
- `MTVEC_BASE`: 28 bits size bit vector holds the base address;

In current implementation, `MTVEC_MODE` only store two mode. A write with
`value[1:0]` larger or equals to 2 is considered as implementation bug.

```asl
enumeration MTVEC_MODE_TYPE {
  MTVEC_DIRECT_MODE,
  MTVEC_VECTORED_MODE
};

var MTVEC_MODE : MTVEC_MODE_TYPE;

getter MTVEC_MODE_BITS => bits(2)
begin
  case MTVEC_MODE of
    when MTVEC_DIRECT_MODE => return '00';
    when MTVEC_VECTORED_MODE => return '01';
  end
end

setter MTVEC_MODE_BITS = value : bits(2)
begin
  case value of
    when '00' => MTVEC_MODE = MTVEC_DIRECT_MODE;
    when '01' => MTVEC_MODE = MTVEC_VECTORED_MODE;
    otherwise => assert FALSE;
  end
end
```

In definition, `MTVEC_BASE` inherently valid. Thus there is no constraint
at architecture states `MTVEC_BASE`.

```asl
func Read_MTVEC() => bits(32)
begin
  return [MTVEC_BASE, MTVEC_MODE_BITS];
end

func Write_MTVEC(value : bits(32)) => Result
begin
  // write to 00 and 01 is valid, write to 10 and 11 is no-op
  if value[1] == '0' then
    MTVEC_MODE_BITS = value[1:0];
  end

  MTVEC_BASE = value[31:2];

  return Retired();
end
```

== Machine Trap Value (mtval) Register

CSR `mtval` will have full 32-bits state register `MTVAL` to store value.
Read and write this architecture states have no constraints.

```asl
var MTVAL : bits(32);

func Write_MTVAL(value : bits(32)) => Result
begin
  MTVAL = value;

  return Retired();
end

func Read_MTVAL() => bits(32)
begin
  return MTVAL;
end
```

== Machine Exception Program Counter (mepc) Register

CSR `mepc` have full 32-bits states register `MEPC` to store value.
Any write to the `MEPC` states must be 32-bits align. (We don't have C extension support now).

```asl
var __MEPC : bits(32);
getter MEPC => bits(32)
begin
  return __MEPC;
end

setter MEPC = pc : bits(32)
begin
  assert pc[0] == '0';
  // todo: test IALIGN before assert after C extension implmented
  assert pc[1] == '0';
  __MEPC = pc;
end
```

Thus developer should verify address before writing to `mepc`.

```asl
func Write_MEPC(value : bits(32)) => Result
begin
  // todo: C ext
  MEPC = [ value[31:2], '00' ];

  return Retired();
end
```

= Trap Handling <trap-handling>

#notes[We use the term exception to refer to an unusual condition occurring at
  run time associated with an instruction in the current RISC-V hart. We use
  the term interrupt to refer to an external asynchronous event that may cause
  a RISC-V hart to experience an unexpected transfer of control. We use the
  term trap to refer to the transfer of control to a trap handler caused by
  either an exception or an interrupt.]

== Exception

Each execution may return a failed `Result`, but the result should continuously
transfer to upper level, and finally get trapped at `Step` function. A trap
exception will update corresponding `mstatus`, `mip`... CSR, and set `PC` to
the address stored in `mtvec` CSR.

```asl
func TrapException(cause : integer, trap_value : bits(32))
begin
  // mepc
  MEPC = PC;

  // mcause
  MCAUSE_IS_INTERRUPT = FALSE;
  // convert integer to 31bits length bit vector
  MCAUSE_XCPT_CODE = asl_cvt_int_bits(cause, 31);

  // mstatus
  MSTATUS_MPIE = MSTATUS_MIE;
  MSTATUS_MIE = '0';
  MSTATUS_MPP = CURRENT_PRIVILEGE;

  // mtval
  MTVAL = trap_value;

  PC = [ MTVEC_BASE, '00' ];
end
```

== Interrupt

Interrupt is check before starting to decode an instruction.
A `CheckInterrupt` function will read corresponding pending and enable bit to
determine if model should trap into the interrupt handler or not.
If there is an interrupt, the `Step` function will directly return instead of
continuing decode and execute.

```asl
func CheckInterrupt() => boolean
begin
  // if machine mode interrupt bit is not enabled, just return
  if MSTATUS_MIE == '0' then
    return FALSE;
  end

  let machine_trap_timer : bit = MTIP AND MTIE;
  if machine_trap_timer == '1' then
    TrapInterrupt(MACHINE_TIMER_INTERRUPT);
    return TRUE;
  end

  let machine_trap_external : bit = MEIP AND MEIE;
  if machine_trap_external == '1' then
    TrapInterrupt(MACHINE_EXTERNAL_INTERRUPT);
    return TRUE;
  end

  return FALSE;
end


func Step()
begin
  let has_interrupt : boolean = CheckInterrupt();
  if has_interrupt then
    return;
  end
// ...
end
```

Handling trap for interrupt has similar logic as handling trap for exception,
but interrupt specific information will be written into CSR, and PC is handled
by least significant bits of `mtvec`.

```asl
func TrapInterrupt(interrupt_code : integer{3,7,11})
begin

  // save current context
  MSTATUS_MPIE = MSTATUS_MIE;
  MSTATUS_MIE = '0';
  MSTATUS_MPP = CURRENT_PRIVILEGE;
  MEPC = PC;

  CURRENT_PRIVILEGE = PRIV_MACHINE_MODE;

  MCAUSE_IS_INTERRUPT = TRUE;
  MCAUSE_XCPT_CODE = asl_cvt_int_bits(interrupt_code, 31);

  if MTVEC_MODE == MTVEC_DIRECT_MODE then
    PC = [ MTVEC_BASE, '00' ];
  else
    PC = [ MTVEC_BASE, '00' ] + (4 * interrupt_code);
  end
end
```

= Rust Simulator <rust-simulator>

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

=== Memory Read Write APIs

All the memory API have following return type:

```asl
record FFI_ReadResult(N) {
  success : boolean;
  data    : bits(N);
};
```

After monomorphic transformation, this record type will became following C `struct`:

```c
// FFI_ReadResult(32)
typedef struct {
    bool success;
    uint32_t data;
} FFI_ReadResult_N_32;

// FFI_ReadResult(16)
typedef struct {
    bool success;
    uint16_t data;
} FFI_ReadResult_N_16;
```

For memory read, model required following platform implementation:

```asl
func FFI_instruction_fetch(pc : bits(32)) => FFI_ReadResult(32);

func FFI_read_physical_memory_8bits(addr : bits(32)) => FFI_ReadResult(8);
func FFI_read_physical_memory_16bits(addr : bits(32)) => FFI_ReadResult(16);
func FFI_read_physical_memory_32bits(addr : bits(32)) => FFI_ReadResult(32);
```

Which will be translated into following C code:

```c
FFI_ReadResult_N_32 FFI_instruction_fetch_0(uint32_t pc);

FFI_ReadResult_N_8 FFI_read_physical_memory_8bits_0(uint32_t addr);
FFI_ReadResult_N_16 FFI_read_physical_memory_16bits_0(uint32_t addr);
FFI_ReadResult_N_32 FFI_read_physical_memory_32bits_0(uint32_t addr);
```

For memory write, model required following platform implementation:

```asl
func FFI_write_physical_memory_8bits(addr : bits(32), data : bits(8)) => boolean;
func FFI_write_physical_memory_16bits(addr : bits(32), data : bits(16)) => boolean;
func FFI_write_physical_memory_32bits(addr : bits(32), data : bits(32)) => boolean;
```

Platform should return `false` when a write violate platform memory to indicate
an access fault.

In current platform implementation, there is no memory protection, and any
memory violation will be capture and raise an memory load store fault exception
back to model. Unaligned read/write is not supported at memory platform side
and is also thrown as exception.

== Hook APIs

Model required following hooks from platform to notify real time info of model current behavior.

```asl
// Execute when executing fence instruction
func FFI_emulator_do_fence();
// Execute when GPR get written
func FFI_write_GPR_hook(reg_idx: integer{0..31}, data: bits(32));
```

== Trap APIs

Model required platform provide following functions for exception/interrupt trap supports.

```asl
func FFI_machine_external_interrupt_pending() => bit;
func FFI_machine_time_interrupt_pending() => bit;
func FFI_ebreak();
func FFI_ecall();
```

== Debug APIs

Model required platform provide following functions implementation to debug model.

```asl
func FFI_print_str(s: string);
func FFI_print_bits_hex(v: bits(32));
```

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

== Compile final simulator

Developer can get binary with following command:
```console
$ nix build '.#pokedex.simulator'
$ ls ./result/bin
```

Or build the emulator by invoking cargo manually:

```console
$ nix develop '.#pokedex.simulator.dev'
$ cd pokedex/simulator
$ cargo build
```

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

= Appendix B: Coding convention

To ensure consistency and readability across the project, please adhere to the
following conventions when writing ASL code.

== Use Explicit Type Declarations

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

== Avoid Deeply Nested Operations

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

== Declare architecture states with UPPERCASE

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


== Pad Literal Bit Vectors for Clarity

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

== Declare local variable with lower_case

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

== Use double underscore to indicate private value

Function, value binding that should only be used in current file should be prefixed
with double underscore.

Recommended:

```asl
val __MyPrivateValue : bits(32);

func __HandleMyPrivateValue()
begin
end
```

