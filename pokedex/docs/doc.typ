= Introduction

The Pokedex project is an implementation of the RISC-V rv32imafc instruction
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
  [*pokedex*], [
  Our T1 testbench works with a collection of micro-architecture designs, each
  of which is named after a Pokémon. We chose the name Pokedex for this project
  to align with that theme.

  In the world of Pokémon, a Pokédex is an essential tool for understanding the
  creatures you interact with. In the same spirit, this project provides the
  tools to help us better understand, maintain, and improve our T1
  architectures.
  ],

  [*ASL*], [
  ARM Specification Language (ASL) is an executable language for
  writing clear, precise specifications of Instruction Set Architectures
  (ISAs).
  ],

  [*ASLi*], [
  The ASL interpreter (ASLi) is an implementation of ASL that can execute ASL
  specifications either in an interpreter or by compiling via C code.
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

== Coding Convention

To ensure consistency and readability across the project, please adhere to the
following conventions when writing ASL code.

*1. Use Explicit Type Declarations*

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

*2. Avoid Deeply Nested Operations*

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

= Model design
This model implements the RISC-V instruction set architecture based on the
official `riscv-isa-manual` repository. Our implementation specifically adheres
to the latest ratified version released on May 08, 2025.

For reference, developers can download the official specification document for
from the following link:

- RISC-V ISA Specification (Release 2025-05-08):
  https://github.com/riscv/riscv-isa-manual/releases/tag/20250508

== Instruction Sets
This section contains basic information of how to describe instruction semantics.

=== Instruction file convention
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

=== Writing Instruction Semantics
The logic for each instruction is written in its own `.asl` file (e.g.,
`addi.asl`). This file contains *only the body* of the instruction's execution
logic. The `codegen` CLI tool automatically wraps this logic in a full function
signature and adds a call to it from a global dispatcher. Developer *should not*
write the function signature yourself.

*How It Works*
The `codegen` CLI tool processes every `.asl` file within the `extensions/`
directory and performs two main actions:

*1. Generates an Execute<InstructionName> Function*: It creates a unique function
for each instruction. The name is derived from the filename (e.g., `vle64_v.asl`
becomes `ExecuteVle64_v`), and your code snippet is inserted into its body. This
function will always receive the 32-bit instruction opcode as an argument:

```asl
func Execute<InstructionName>(instruction: bits(32))
```

*2. Creates a Dispatch Case*: It adds a pattern match case to the global
`Execute()` function. This dispatcher inspects the opcode of every incoming
instruction and calls the corresponding `Execute<InstructionName>` function.

*Key Responsibilities*

- *Implement Core Logic*: Your code must decode operands from the instruction
  argument, perform the operation, and write the results to the appropriate
  registers (GPRs, CSRs, etc.).
- *Update the Program Counter (PC)*: The main Step() function of the model does
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
*Note* there is no surrounding `func` block.

```asl
// Decode operands from the instruction bits
let imm : bits(11) = instruction[31:20];
let rs1 : integer = UInt(instruction[19:15]);
let rd  : integer = UInt(instruction[11:7]);

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
func ExecuteAddi(instruction : bits(32))
begin
  // code from addi.asl will be inserted here

  // Decode operands from the instruction bits
  let imm : bits(11) = instruction[31:20];
  let rs1 : integer = UInt(instruction[19:15]);
  let rd  : integer = UInt(instruction[11:7]);

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
      ExecuteAddi(instruction);
    // ...
    otherwise =>
      ThrowException(IllegalInstruction);
  end
end
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
      return ReadMstatus();
    when '001_100_000_001' =>
      return ReadMisa();
    // ...
    otherwise =>
      ThrowException(ExceptionType);
  end
end

func WriteCSR(csr_number: bits(12), value: bits(xlen))
begin
  case csr_number of
    when '001_100_000_000' =>
      WriteMstatus(value);
    when '001_100_000_001' =>
      WriteMisa(value);
    otherwise =>
      ThrowException(ExceptionType);
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
The tool generates a read function named `Read<CsrName>`. Your `.asl` file in
the `csr/read/` directory must contain the logic to return the CSR's value as
`bits(xlen)` type.

*Write Handlers:*
The tool generates a write function named `Write<CsrName>`. Your `.asl` file in
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
the body of the `ReadMisa()` function.
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
the `WriteMisa(value: bits(32))` function.
```asl
// The 'I' bit (bit 8) of MISA is the only writable bit in this example.
misa_i = value[8];
return TRUE;
```

*4. Final Generated Code*

After running `codegen` CLI, the tool will take your `.asl` snippets and
produce the following complete, callable functions:

```asl
func ReadMisa() => bits(32)
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

func WriteMisa(value : bits(32)) => boolean
begin
    // The 'I' bit (bit 8) of MISA is the only writable bit in this example.
    misa_i = value[8];
    return TRUE;
end
```
