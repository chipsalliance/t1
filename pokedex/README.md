## Contents

1.Introduction <span style="display: inline-block;"></span> ⁠1

1.1.Glossary <span style="display: inline-block;"></span> ⁠1

1.2.How to build this document
<span style="display: inline-block;"></span> ⁠1

1.3.Resources <span style="display: inline-block;"></span> ⁠1

1.4.Coding Convention <span style="display: inline-block;"></span> ⁠1

2.Instruction Sets <span style="display: inline-block;"></span> ⁠1

2.1.Instruction file convention
<span style="display: inline-block;"></span> ⁠1

2.2.Writing Instruction Semantics
<span style="display: inline-block;"></span> ⁠1

2.3.Arg Luts <span style="display: inline-block;"></span> ⁠1

2.4.CSR <span style="display: inline-block;"></span> ⁠1

2.4.1.Reading and Writing CSRs
<span style="display: inline-block;"></span> ⁠1

2.4.2.Implementing CSR Handlers
<span style="display: inline-block;"></span> ⁠1

2.4.2.1.Example: Implementing
<span style="display: inline-block;">`misa`</span>
<span style="display: inline-block;"></span> ⁠1

3.Architecture States <span style="display: inline-block;"></span> ⁠1

3.1.General Propose Register (GPRs)
<span style="display: inline-block;"></span> ⁠1

3.2.Control and Status Register (CSRs)
<span style="display: inline-block;"></span> ⁠1

3.2.1.Read only zeros CSRs <span style="display: inline-block;"></span>
⁠1

3.2.2.Machine ISA (misa) Register
<span style="display: inline-block;"></span> ⁠1

3.2.3.Machine status (mstatus) Register
<span style="display: inline-block;"></span> ⁠1

3.2.4.Machine Cause (mcause) Register
<span style="display: inline-block;"></span> ⁠1

3.2.5.Machine Interrupt (mip and mie) Registers
<span style="display: inline-block;"></span> ⁠1

4.Rust Simulator <span style="display: inline-block;"></span> ⁠1

4.1.File Structures <span style="display: inline-block;"></span> ⁠1

4.2.Development Setup <span style="display: inline-block;"></span> ⁠1

4.3.Simulator States <span style="display: inline-block;"></span> ⁠1

4.4.FFI functions <span style="display: inline-block;"></span> ⁠1

5.Differential Tests <span style="display: inline-block;"></span> ⁠1

6.Build Instruction <span style="display: inline-block;"></span> ⁠1

6.1.Debug generated code <span style="display: inline-block;"></span> ⁠1

6.2.Get archived ASL library
<span style="display: inline-block;"></span> ⁠1

7.Appendix A: <span style="display: inline-block;">`ASLi`</span>
commands for lowering <span style="display: inline-block;"></span> ⁠1

7.1.Filter unreachable code from exports
<span style="display: inline-block;"></span> ⁠1

7.2.Eliminate <span style="display: inline-block;">`typedef`</span>
<span style="display: inline-block;"></span> ⁠1

7.3.Eliminate bit and int arithmetic operation
<span style="display: inline-block;"></span> ⁠1

7.4.Eliminate bit vector concatenate
<span style="display: inline-block;"></span> ⁠1

7.5.Convert bit-slice operation
<span style="display: inline-block;"></span> ⁠1

7.6.Eliminate slices of integers
<span style="display: inline-block;"></span> ⁠1

7.7.Convert getter/setter <span style="display: inline-block;"></span> ⁠1

7.8.TODO <span style="display: inline-block;"></span> ⁠1

7.9.Constant propagation <span style="display: inline-block;"></span> ⁠1

7.10.Monomorphicalize functions
<span style="display: inline-block;"></span> ⁠1

7.11.Lift let-expressions <span style="display: inline-block;"></span> ⁠1

7.12.Convert bit vector slice operation to bit operation
<span style="display: inline-block;"></span> ⁠1

7.13.Convert match to if <span style="display: inline-block;"></span> ⁠1

7.14.Wrap variable <span style="display: inline-block;"></span> ⁠1

7.15.Create bounded int <span style="display: inline-block;"></span> ⁠1

7.16.Filter function not listed in imports
<span style="display: inline-block;"></span> ⁠1

7.17.Check monomorphic <span style="display: inline-block;"></span> ⁠1

## 1. Introduction

The Pokedex project is an implementation of the RISC-V
<span style="display: inline-block;">`rv32imafc`</span> instruction set
architecture (ISA). It includes a formal specification, an instruction
decoder, and an emulator designed for functional verification.

A primary goal of this project is to provide a simple, maintainable, and
flexible design. This allows developers to easily add new instructions
or make architectural changes—capabilities not easily supported by
existing tools like
<span style="display: inline-block;">`riscv/sail-riscv`</span> or Spike
(<span style="display: inline-block;">`riscv/riscv-isa-sim`</span>).

The emulator can be linked with the T1 testbench to enable full
functional verification, including the standard RVV and custom
extensions.

This guide explains the ASL (ARM Specification Language) model used in
the Pokedex project. It provides the necessary information for a
developer to:

-   Create functional model for RISC-V.
-   Serve as a reference model for RTL design.
-   Add custom instruction with low effort.

### 1.1. Glossary

<table>
<colgroup>
<col style="width: 50%" />
<col style="width: 50%" />
</colgroup>
<tbody>
<tr class="odd">
<td><strong>Name</strong></td>
<td><strong>Notes</strong></td>
</tr>
<tr class="even">
<td><strong>pokedex</strong></td>
<td><p>Our T1 testbench works with a collection of micro-architecture
designs, each of which is named after a Pokémon. We chose the name
Pokedex for this project to align with that theme.</p>
<p>In the world of Pokémon, a Pokédex is an essential tool for
understanding the creatures you interact with. In the same spirit, this
project provides the tools to help us better understand, maintain, and
improve our T1 architectures.</p></td>
</tr>
<tr class="odd">
<td><strong>ASL</strong></td>
<td>ARM Specification Language (ASL) is an executable language for
writing clear, precise specifications of Instruction Set Architectures
(ISAs).</td>
</tr>
<tr class="even">
<td><strong>ASLi</strong></td>
<td>The ASL interpreter (ASLi) is an implementation of ASL that can
execute ASL specifications either in an interpreter or by compiling via
C code.</td>
</tr>
<tr class="odd">
<td><strong>GPR</strong></td>
<td>Short term for General Propose Register</td>
</tr>
<tr class="even">
<td><strong>CSR</strong></td>
<td>Short term for Control and Status Register</td>
</tr>
</tbody>
</table>

### 1.2. How to build this document

Document are written in
<span style="display: inline-block;">`docs/doc.typ`</span> file.
Developer should first install typst and pandoc in their system
environment.

-   Build README

<!-- -->

    make doc

-   Build PDF

<!-- -->

    make doc-pdf

### 1.3. Resources

-   ASL Document hosted by Intel Lab:
    <https://intellabs.github.io/asl-interpreter/asl_reading.html>
-   ASL specification:
    <https://developer.arm.com/documentation/ddi0626/latest>
-   RISC-V ISA Specification:
    <https://github.com/riscv/riscv-isa-manual/>
-   ASL Prelude Reference:
    <https://github.com/IntelLabs/asl-interpreter/blob/master/prelude.asl>
    (Can also be obtained by running command
    <span style="display: inline-block;">`asli --print_spec`</span>)

### 1.4. Coding Convention

To ensure consistency and readability across the project, please adhere
to the following conventions when writing ASL code.

**Use Explicit Type Declarations**

Always provide a type annotation when declaring a variable with
<span style="display: inline-block;">`let`</span> or
<span style="display: inline-block;">`var`</span>. This practice
improves code clarity and helps prevent type-related errors.

Recommended:

    let i : integer = 0x1;

Avoid:

    let i = 0x1;

**Avoid Deeply Nested Operations**

Instead of nesting multiple function calls or operations in a single
statement, use intermediate variables to store the results of each step.
This makes the logic easier to read, understand, and debug.

Recommended:

    let a_ext : bits(32) = ZeroExtend(GPR[1]);
    let not_a : bits(32) = NOT(a_ext);

Avoid:

    let not_a : bits(32) = NOT(ZeroExtend(GPR[1]));

**Declare architecture states with UPPERCASE**

Architecture states reads like global variable in normal code, thus we
prefer using UPPERCASE for the states variable name.

Recommended:

    var MPP : bits(2)

Avoid:

    var mpp : bits(2)

**Pad Literal Bit Vectors for Clarity**

In the ASL specification, single quotes are used to define literal bit
vectors (e.g., <span style="display: inline-block;">`'1'`</span>).
However, this syntax can be confusing for developers familiar with other
languages, where single quotes often denote a character or string.

To improve code clarity and avoid ambiguity, we recommend zero-padding
all literal bit vectors to a minimum width of 4 bits.

Recommended:

    GPR[rd] = ZeroExtend('0001');

Avoid:

    GPR[rd] = ZeroExtend('1');

**Declare local variable with lower\_case**

Function local variable should be declared in "lower\_case".

Recommended:

    func a(rs1 : bits(5))
    begin
      let shift_amount : integer = UInt(X[rs1]);
    end

Avoid:

    func a(rs1 : bits(5))
    begin
      let shiftAmount : integer = UInt(X[rs1]);
      let ShiftAmount : integer = UInt(X[rs1]);
      let SHIFT_AMOUNT : integer = UInt(X[rs1]);
    end

## 2. Instruction Sets

This model implements the RISC-V instruction set architecture based on
the official
<span style="display: inline-block;">`riscv-isa-manual`</span>
repository. Our implementation specifically adheres to the latest
ratified version released on May 08, 2025.

For reference, developers can download the official specification
document for from the following link:

-   **RISC-V ISA Specification (Release 2025-05-08)**:
    <https://github.com/riscv/riscv-isa-manual/releases/tag/20250508>

This section contains basic information of how to describe instruction
semantics.

### 2.1. Instruction file convention

The implementation logic for each instruction is defined in its own
<span style="display: inline-block;">`.asl`</span> file. These files are
organized into directories, with each directory corresponding to a
specific RISC-V instruction set.

The directory structure for instruction sets must follow the official
<span style="display: inline-block;">`riscv-opcodes`</span> repository.
Each instruction set is represented by a directory. And the directory's
name must exactly match the corresponding extension filename found
within the <span style="display: inline-block;">`extensions/`</span>
directory of the
<span style="display: inline-block;">`riscv-opcodes`</span> repository.

Within each directory, the filename for an instruction must strictly
follow the
<span style="display: inline-block;">`<instruction_name>.asl`</span>
format:

-   The <span style="display: inline-block;">`<instruction_name>`</span>
    must be the lowercase version of the instruction.
-   Any dot (<span style="display: inline-block;">`.`</span>) in an
    instruction's name must be replaced with an underscore
    (<span style="display: inline-block;">`_`</span>).

Finally, to mirror the layout of the official
<span style="display: inline-block;">`riscv-opcodes`</span> repository,
all of the previously mentioned instruction set directories
(<span style="display: inline-block;">`rv_i`</span>,
<span style="display: inline-block;">`rv_v`</span>, etc.) must be placed
inside a single top-level directory named
<span style="display: inline-block;">`extensions`</span>.

**Example**

Given the instructions
<span style="display: inline-block;">`slli`</span>,
<span style="display: inline-block;">`addi`</span> and
<span style="display: inline-block;">`vle64.v`</span>, the resulting
directory and file structure would be:

    model/
    └── extensions/
        ├── rv_i/
        │   └── addi.asl
        ├── rv_v/
        │   └── vle64_v.asl
        └── rv32_i/
            └── slli.asl

### 2.2. Writing Instruction Semantics

The logic for each instruction is written in its own
<span style="display: inline-block;">`.asl`</span> file (e.g.,
<span style="display: inline-block;">`addi.asl`</span>). This file
contains **only the body** of the instruction's execution logic. The
<span style="display: inline-block;">`codegen`</span> CLI tool
automatically wraps this logic in a full function signature and adds a
call to it from a global dispatcher. Developer **should not** write the
function signature yourself.

**How It Works**

The <span style="display: inline-block;">`codegen`</span> CLI tool
processes every <span style="display: inline-block;">`.asl`</span> file
within the <span style="display: inline-block;">`extensions/`</span>
directory and performs two main actions:

**1. Generates an
<span style="display: inline-block;">`Execute_<INSTRUCTION_NAME>`</span>
Function**: It creates a unique function for each instruction. The name
is derived from the filename (e.g.,
<span style="display: inline-block;">`vle64_v.asl`</span> becomes
<span style="display: inline-block;">`Execute_VLE64_V`</span>), and your
code snippet is inserted into its body. This function will always
receive the 32-bit instruction opcode as an argument:

    func Execute_<INSTRUCTION_NAME>(instruction: bits(32))

**2. Creates a Dispatch Case**: It adds a pattern match case to the
global <span style="display: inline-block;">`Execute()`</span> function.
This dispatcher inspects the opcode of every incoming instruction and
calls the corresponding
<span style="display: inline-block;">`Execute_<INSTRUCTION_NAME>`</span>
function.

**Key Responsibilities**

-   **Implement Core Logic**: Your code must decode operands from the
    instruction argument, perform the operation, and write the results
    to the appropriate registers (GPRs, CSRs, etc.).
-   **Update the Program Counter (PC)**: The main
    <span style="display: inline-block;">`Step()`</span> function of the
    model does not automatically increment the PC. Your instruction
    logic is responsible for updating the PC value after execution
    (e.g., <span style="display: inline-block;">`PC = PC + 4;`</span>).
    Forgetting this step will cause the model to loop on the same
    instruction.

**Example: implementing the
<span style="display: inline-block;">`addi`</span> instruction**

Here is a complete walkthrough for implementing the addi instruction.

**Step 1: Create the Instruction File**

First, create the <span style="display: inline-block;">`addi.asl`</span>
file and place it in the correct directory according to the convention:
<span style="display: inline-block;">`extensions/rv_i/addi.asl`</span>.

**Step 2: Write the Implementation Logic**

Inside <span style="display: inline-block;">`addi.asl`</span>, write the
code to perform the "add immediate" operation.

> Note there is no surrounding
> <span style="display: inline-block;">`func`</span> block.

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

**Step 3: Review the Generated Code**

After running the <span style="display: inline-block;">`codegen`</span>
tool, the <span style="display: inline-block;">`addi.asl`</span> snippet
will be integrated into the model. The final generated code will look
like this:

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

### 2.3. Arg Luts

To reduce decoding bugs and simplify development, the codegen-cli tool
automatically generates helper functions for extracting argument fields
from an instruction's opcode.

This process is driven by the
<span style="display: inline-block;">`arg_luts.csv`</span> file from the
official <span style="display: inline-block;">`riscv-opcodes`</span>
repository, which contains a lookup table specifying the name and bit
position for each instruction argument.

The generated APIs follow a consistent naming and signature convention:

-   Naming: All functions start with a
    <span style="display: inline-block;">`GetArg_`</span> prefix,
    followed by the field name in uppercase (e.g.,
    <span style="display: inline-block;">`GetArg_RD`</span>,
    <span style="display: inline-block;">`GetArg_RS1`</span>).
-   Signature: Each function accepts the 32-bit instruction as a
    <span style="display: inline-block;">`bits(32)`</span> parameter and
    returns a bit vector whose size is determined by the field's
    definition in the lookup table.

For example, instead of manually slicing the immediate field from an
I-type instruction, a developer can use the generated
<span style="display: inline-block;">`GetArg_ZIMM12`</span> API. This
function takes the 32-bits instruction and returns the corresponding
12-bit immediate value.

    func GetArg_ZIMM12(instruction : bits(32) => bits(12);

### 2.4. CSR

This section contains basic information of how to implement Control and
Status Register (CSR) to models.

#### 2.4.1. Reading and Writing CSRs

All CSRs operations are handled by two main APIs that are generated by
the <span style="display: inline-block;">`codegen`</span> CLI tool.

-   Read API:
    <span style="display: inline-block;">`func ReadCSR(csr_number: bits(12)) => bits(xlen)`</span>
-   Write API:
    <span style="display: inline-block;">`func WriteCSR(csr_number: bits(12), value: bits(xlen))`</span>

The generated code works as a dispatcher. It uses pattern matching on
the <span style="display: inline-block;">`csr_number`</span> parameter
to identify the target CSR and then calls the corresponding handler
function that you have implemented for that specific register.

For example, the generated code will look similar to this:

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

#### 2.4.2. Implementing CSR Handlers

While the <span style="display: inline-block;">`codegen`</span> CLI tool
creates the main dispatcher and the function signatures, developers are
responsible for implementing the specific read and write logic for each
CSR.

This is done by providing the body of the function in a dedicated
<span style="display: inline-block;">`.asl`</span> file. The build
process then combines the provided logic with the generated function
signature.

Here is the step-by-step process to add a new CSR or modify an existing
one.

**Step 1: Create the Handler Files**

The logic for each CSR operation lives in its own file. The
<span style="display: inline-block;">`codegen`</span> CLI tool looks for
these files in two directories:

-   <span style="display: inline-block;">`csr/read/`</span> for read
    operations.
-   <span style="display: inline-block;">`csr/write/`</span> for write
    operations.

The <span style="display: inline-block;">`codegen`</span> CLI tool
identifies each CSR and its corresponding address directly from the
<span style="display: inline-block;">`.asl`</span> filename. This
file-based mapping requires a strict naming convention.

The required format is:
<span style="display: inline-block;">`<csr_name>_<address>.asl`</span>

-   <span style="display: inline-block;">`<csr_name>`</span>: The name
    of the CSR in lowercase (e.g.,
    <span style="display: inline-block;">`mcycle`</span>).
-   <span style="display: inline-block;">`_`</span>: An underscore used
    as a separator.
-   <span style="display: inline-block;">`<address>`</span>: The CSR
    address represented as a lowercase hexadecimal string, **without the
    <span style="display: inline-block;">`0x`</span> prefix** (e.g.,
    <span style="display: inline-block;">`b00`</span> for address
    <span style="display: inline-block;">`0xB00`</span>).

For example, to implement the
<span style="display: inline-block;">`mcycle`</span> CSR, you would
create files named
<span style="display: inline-block;">`csr/read/mcycle_b00.asl`</span>
and
<span style="display: inline-block;">`csr/write/mcycle_b00.asl`</span>.

**Step 2: Implement the Handler Logic**

You only need to write the code that goes inside the function body. The
tool generates the function signature around your code.

**Read Handlers:** The tool generates a read function named
<span style="display: inline-block;">`Read_<CSR_NAME>`</span>. Your
<span style="display: inline-block;">`.asl`</span> file in the
<span style="display: inline-block;">`csr/read/`</span> directory must
contain the logic to return the CSR's value as
<span style="display: inline-block;">`bits(xlen)`</span> type.

**Write Handlers:** The tool generates a write function named
<span style="display: inline-block;">`Write_<CSR_NAME>`</span>. Your
<span style="display: inline-block;">`.asl`</span> file in the
<span style="display: inline-block;">`csr/write/`</span> directory must
contain the logic to handle the write operation. The new value is passed
in as the <span style="display: inline-block;">`value`</span> argument
with <span style="display: inline-block;">`XLEN`</span> bits.

##### 2.4.2.1. Example: Implementing <span style="display: inline-block;">`misa`</span>

Let's walk through the complete process for adding the
<span style="display: inline-block;">`misa`</span> (at
<span style="display: inline-block;">`0x301`</span>) CSR.

**1. Create the File Structure**

Create the empty
<span style="display: inline-block;">`misa_301.asl`</span> files in the
correct directories. Your file structure should look like this:

    model/
    └── csr/
        ├── csrs.csv
        ├── read/
        │   └── misa_301.asl
        └── write/
            └── misa_301.asl

**2. Implement the Read Logic
(<span style="display: inline-block;">`read/misa_301.asl`</span>)**

Add the CSR read logic to
<span style="display: inline-block;">`csr/read/misa_301.asl`</span>.
This code snippet will become the body of the
<span style="display: inline-block;">`Read_MISA()`</span> function.

    // This logic assumes only rv32i is supported. The I-bit is controlled
    // by the 'misa_i' register, and all other non-MXL bits are read-only-zero.

    // Reports XLEN is 32
    let mxl : bits(2) = '01';

    // RO zero (4 bits) + Z to I (17 bits)
    let ro_zero : bits(21) = Zeros(21);

    // Assuming 'misa_i' is a global register defined elsewhere.
    return [mxl, ro_zero, misa_i, '000', NOT(misa_i), '000'];

**3. Implement the Write Logic
(<span style="display: inline-block;">`write/misa_301.asl`</span>)**

Add the corresponding logic to
<span style="display: inline-block;">`csr/write/misa_301.asl`</span>.
This becomes the body of the
<span style="display: inline-block;">`Write_MISA(value: bits(32))`</span>
function.

    // The 'I' bit (bit 8) of MISA is the only writable bit in this example.
    misa_i = value[8];
    return TRUE;

**4. Final Generated Code**

After running <span style="display: inline-block;">`codegen`</span> CLI,
the tool will take your
<span style="display: inline-block;">`.asl`</span> snippets and produce
the following complete, callable functions:

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

## 3. Architecture States

All architectural states for current ISA model, from general-purpose
registers to Control and Status Registers, are defined in the
<span style="display: inline-block;">`states.asl`</span> file. To
optimize the model, we only define the specific bits necessary for the
supported ISA features.

This section serves as a reference for all architectural states
maintained by the model.

### 3.1. General Propose Register (GPRs)

This model supports the <span style="display: inline-block;">`I`</span>
extension, providing 32 general-purpose registers
(<span style="display: inline-block;">`x0`</span> through
<span style="display: inline-block;">`x31`</span>). Because the
<span style="display: inline-block;">`x0`</span> register is a special
case (hardwired to zero), our implementation only declares a 31-element
array to store the state for registers
<span style="display: inline-block;">`x1`</span> through
<span style="display: inline-block;">`x31`</span>.

    // Internal General Propose Register
    var __GPR : array[31] of bits(32)

The <span style="display: inline-block;">`__GPR`</span> variable is an
internal architecture states. Access to the GPRs is managed by a global
array-like variable <span style="display: inline-block;">`X`</span>.
This provide a clean interface using ASL's getter and setter keyword,
allows developers to use standard array indexing
(<span style="display: inline-block;">`X[i]`</span>) while the
underlying logic handles the special case of
<span style="display: inline-block;">`x0`</span>.

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

When <span style="display: inline-block;">`X[0]`</span> is read, the
getter intercepts the call and returns a 32-bit zero vector, without
accessing the <span style="display: inline-block;">`__GPR`</span> array.
A write to <span style="display: inline-block;">`x0`</span> is silently
ignored by the setter logic, preserving its hardwired-zero behavior.

When any other register
(<span style="display: inline-block;">`X[1]`</span>-<span style="display: inline-block;">`X[31]`</span>)
is accessed, the getter adjusts the index by
<span style="display: inline-block;">`-1`</span> and returns the
corresponding value from the
<span style="display: inline-block;">`__GPR`</span> array. A write to
any register from <span style="display: inline-block;">`x1`</span>
through <span style="display: inline-block;">`x31`</span> updates its
value in the <span style="display: inline-block;">`__GPR`</span> array.

All access to <span style="display: inline-block;">`X`</span> has a
integer constraint check declare by
<span style="display: inline-block;">`integer{0..31}`</span>, which
allows only integer from 0 to 31. This constraints are checked by ASLi
when <span style="display: inline-block;">`--check-constraints`</span>
flag is provided.

### 3.2. Control and Status Register (CSRs)

This sections contains CSRs behavior in current model. If a CSR address
not contains in this section get read or write, the dispatcher will
raise illegal instruction exception.

#### 3.2.1. Read only zeros CSRs

Registers covered in this section are always read-only zero. Any write
to these registers is a no-op and will not have any side effects. Also
no architecture states will be allocated for these CSRs.

-   <span style="display: inline-block;">`mvendorid`</span>
-   <span style="display: inline-block;">`marchid`</span>
-   <span style="display: inline-block;">`mimpid`</span>
-   <span style="display: inline-block;">`mhartid`</span>
-   <span style="display: inline-block;">`mconfigptr`</span>

#### 3.2.2. Machine ISA (misa) Register

Switching extension supports at runtime is not supported by this model.
The <span style="display: inline-block;">`misa`</span> register is
implemented as a read-only CSR register. A read to
<span style="display: inline-block;">`misa`</span> register always
return a static bit vector indicating current enabled extensions. Any
writes to <span style="display: inline-block;">`misa`</span> register
will not change value or have any side effects.

    // read to MISA
    // This will always be a static value represent current hart supported ISA
    let value : bits(32) = ReadCSR(csr_addr);
    // This will do nothing
    WriteCSR(csr_addr, 0x00000000);

Since <span style="display: inline-block;">`misa`</span> is a read-only
value, no states will be allocated in current model.

#### 3.2.3. Machine status (mstatus) Register

Current model focus on machine mode only, supervisor and user mode are
not implemented. So for
<span style="display: inline-block;">`mstatus`</span> register, model
only required following fields in
<span style="display: inline-block;">`mstatus`</span> register:

-   <span style="display: inline-block;">`mie`</span>: global
    interrupt-enable bit for machine mode
-   <span style="display: inline-block;">`mpie`</span>: the
    interrupt-enable bit active prior to the trap
-   <span style="display: inline-block;">`mpp[1:0]`</span>: previous
    privilege level

The <span style="display: inline-block;">`mstatush`</span> register is
also not required if we only implement M-mode.

The above bit fields will be declare as individual register in
<span style="display: inline-block;">`states.asl`</span> file.

    var MSTATUS_MIE  : bit
    var MSTATUS_MPIE : bit
    var MSTATUS_MPP  : bits(2)

An example read/write operation to
<span style="display: inline-block;">`mstatus`</span> register looks
like following:

    func Read_MSTATUS()
    begin
      return [Zeros(18), MSTATUS_MPP, '000', MSTATUS_MPIE, '000', MSTATUS_MIE, '000'];
    end

    func Write_MSTATUS(value : bits(32))
    begin
      MIE = value[3];
      MPIE = value[7];
      MPP = value[12..11];
    end

#### 3.2.4. Machine Cause (mcause) Register

The <span style="display: inline-block;">`mcause`</span> register is
store in two states: a
<span style="display: inline-block;">`Interrupt`</span> bit register and
a 31 bits length exception code register:

    var MCAUSE_INTERRUPT : bit
    var MCAUSE_EXCEPTION_CODE : bits(31)

A read to the <span style="display: inline-block;">`mcause`</span> CSR
register will have a concatenated 32-bit value from above register, with
the interrupt bit at top, exception code value at bottom.

    function Read_MCAUSE()
    begin
      return [MCAUSE_INTERRUPT, MCAUSE_EXCEPTION_CODE];
    end

A write to the <span style="display: inline-block;">`mcause`</span> CSR
register will only write to the
<span style="display: inline-block;">`MCAUSE_EXCEPTION_CODE`</span>
register.

    function Write_MCAUSE(value : bits(32))
    begin
      MCAUSE_EXCEPTION_CODE = value[31:0];
    end

#### 3.2.5. Machine Interrupt (mip and mie) Registers

## 4. Rust Simulator

The ASL model itself is designed as a passive library. Its sole
responsibility is to define the RISC-V instruction set architecture,
including instruction semantics and architecture states. The ASL code
does not contain an entry point (main function), an event loop, or
memory management. It only describes the core logic of the processor.

The execution environment is provided by a simulator written in Rust.
This component acts as the driver for the ASL model and is responsible
for all runtime operations, including loading executable file into
memory, maintaining memory state, driving the model to consume
instructions and recording each change happens in model architecture
states.

### 4.1. File Structures

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

### 4.2. Development Setup

To correctly configure rust-analyzer and export the necessary
environment variables for the project, run the following command:

    nix develop '.#pokedex.simulator.dev'

### 4.3. Simulator States

We maintain following states at Rust side

    pub struct Simulator {
        pc: u32,
        memory: Vec<u8>,
        exception: Option<SimulationException>,

        last_instruction: u32,
        last_instruction_met_count: u8,
        max_same_instruction: u8,

        statistic: Statistic,
    }

The <span style="display: inline-block;">`pc`</span> field holds the
address of the instruction currently being executed. Its primary purpose
is to ensure accurate logging for differential testing against Spike.

In our ASL model, the program counter is updated immediately during an
instruction's execution; there is no
<span style="display: inline-block;">`nextPC`</span> state. This
architectural choice creates a logging challenge for instructions like
<span style="display: inline-block;">`jalr`</span>, which both modify a
register and jump to a new address.

If we logged the register write after
<span style="display: inline-block;">`PC`</span> was updated, the log
would incorrectly show the write occurring at the jump **destination**
address, not at the address of the
<span style="display: inline-block;">`jalr`</span> instruction itself.
This would cause a mismatch when comparing our execution trace with
Spike's.

To solve this, the simulator's
<span style="display: inline-block;">`pc`</span> field is updated after
the model executes the instruction. This field provides a stable record
of the instruction's own address. When logging state changes, such as a
register write, we use this value to ensure the log entry is correctly
associated with the instruction that caused it, guaranteeing an accurate
comparison with the reference log from Spike.

The <span style="display: inline-block;">`memory`</span> field is a
simple, large, byte indexed array. It will be read and written by
corresponding FFI functions from model, it will also be written when
first loading executable file into memory.

The simulator includes an
<span style="display: inline-block;">`exception`</span> field designed
to capture events that occur within the guest executable and are caught
by the host simulator. This field is used for recoverable or expected
events originating from the code being simulated, including infinite
loop, executable notify a power off signal…etc.

> It is critical to distinguish between exceptions that occur within the
> guest executable versus runtime errors that originate in the simulator
> itself. For example, an unaligned read/write should be treat as guest
> exception. However a memory operation requested with an address size
> that cannot fit into unsigned 32-bit integer represents a fundamental
> flaw and should be immediately rejected with fatal error, not
> considered recoverable.

To avoid spamming the terminal during an infinite exception loop—a
common issue for those familiar with Spike—this simulator includes a
mechanism to detect and halt prolonged instruction repetition. This
feature helps diagnose potential infinite loops by tracking if the same
instruction is executed too many times consecutively. It is controlled
by the following fields:

-   <span style="display: inline-block;">`last_instruction`</span>:
    Stores the opcode of the most recently executed instruction.
-   <span style="display: inline-block;">`last_instruction_met_count`</span>:
    A counter that increments each time the current instruction's opcode
    matches last\_instruction. It resets if a different instruction is
    executed.
-   <span style="display: inline-block;">`max_same_instruction`</span>:
    A configurable limit. If
    <span style="display: inline-block;">`last_instruction_met_count`</span>
    exceeds this value, the simulator will halt execution and report an
    infinite loop exception.

The <span style="display: inline-block;">`statistic`</span> field
contains counters to record runtime information that will be reported
after simulation end.

### 4.4. FFI functions

Following are FFI functions required from ASL model that developers
should implemented on Rust side. These function are explicitly declare
at <span style="display: inline-block;">`external.asl`</span> file.

> Due to monomorphic optimization, generated C functions will be suffix
> with unique ID and data size. Developers need to make sure that a
> correct function symbol is declared. They can check final function
> signatures at <span style="display: inline-block;">`*_types.h`</span>
> header file after ASLi generated C files.

-   Memory R/W APIs

    -   <span style="display: inline-block;">`void FFI_write_physical_memory_{8,16,32}bits_0(uint32_t addr, uint{8,16,32}_t data)`</span>
    -   <span style="display: inline-block;">`uint{8,16,32}_t FFI_read_physical_memory_{8,16,32}bits_0(uint32_t addr)`</span>
    -   <span style="display: inline-block;">`uint32_t FFI_instruction_fetch_0(uint32_t pc)`</span>

All the FFI read/write memory APIs with "physical" infix operates on
memory directly. This means there is no memory protection, and memory
violation will immediately raise fatal error at host emulator. Unaligned
read/write should be manually detect at model side. And out-of-memory
write is OK to break the host emulator.

-   Hook APIs

    -   <span style="display: inline-block;">`void FFI_write_GPR_hook_0(signed _BitInt(6) reg_idx, uint32_t data)`</span>
    -   <span style="display: inline-block;">`bool FFI_is_reset_0()`</span>
    -   <span style="display: inline-block;">`void FFI_emulator_do_fence_0()`</span>
    -   <span style="display: inline-block;">`void FFI_ebreak_0()`</span>
    -   <span style="display: inline-block;">`void FFI_ecall_0()`</span>

TODO: explain debug message format

-   Debug APIs

    -   <span style="display: inline-block;">`void FFI_print_string_0(const char* s)`</span>
    -   <span style="display: inline-block;">`void FFI_print_bits_hex_0(uint32_t d)`</span>

Current implementation can be found in
<span style="display: inline-block;">`simulator/simulator/pokedex/src/ffi.rs`</span>.

## 5. Differential Tests

## 6. Build Instruction

### 6.1. Debug generated code

Developers can run following commands to get full source code prepare
for ASLi to compile.

    $ cd pokedex/model
    $ nix develop ".#pokedex.sim-lib" -c make project
    $ ls ./build/1-rvcore
    arg_lut.asl  asl2c.prj  csr_op.asl  execute.asl  external.asl  project.json  states.asl  step.asl

The <span style="display: inline-block;">`project`</span> target will
run <span style="display: inline-block;">`codegen`</span> CLI and copy
manually implemented architecture states ASL implementation into one
folder, with the <span style="display: inline-block;">`asl2c.prj`</span>
lowering script and
<span style="display: inline-block;">`project.json`</span>
configuration.

File <span style="display: inline-block;">`asl2c.prj`</span> contains a
list of optimization and lowering pass ASLi needs to run. Lowering pass
used in <span style="display: inline-block;">`asl2c.prj`</span> is
documented at chapter Appendix A.

> Note that the default optimization
> <span style="display: inline-block;">`:xform_constprop`</span> is not
> enabled, details at
> [<span style="display: inline-block;">`IntelLab/asl-interpreter issue#105`</span>](https://github.com/IntelLabs/asl-interpreter/issues/105)

File <span style="display: inline-block;">`project.json`</span> record
all the functions used for FFI. The
<span style="display: inline-block;">`imports`</span> field records
unimplemented function that needs to be linked from other sources. The
<span style="display: inline-block;">`exports`</span> field records all
the functions that is required by outside library.

Developers can run following commands to get ASLi generated C code:

    $ cd pokedex/model
    $ nix develop ".#pokedex.sim-lib" -c make asl2c
    $ ls ./build/2-cgen
    dumps/  pokedex-sim_exceptions.c  pokedex-sim_exceptions.h  pokedex-sim_funs.c  pokedex-sim_types.h  pokedex-sim_vars.c  pokedex-sim_vars.h

The <span style="display: inline-block;">`dumps/`</span> directory
collect ASL code dumps after each lowering pass, developers can debug
the optimization by inspect those dump file.

### 6.2. Get archived ASL library

Developers can get the archive file with following commands

    $ cd pokedex/model
    $ nix develop ".#pokedex.sim-lib" -c make install
    $ ls ./build/simlib
    include/  lib/

Generated headers are placed under the
<span style="display: inline-block;">`include/`</span> directory, and
the ASL model code is packaged under
<span style="display: inline-block;">`lib/`</span> directory.

## 7. Appendix A: <span style="display: inline-block;">`ASLi`</span> commands for lowering

### 7.1. Filter unreachable code from exports

This command discard any code not reachable from the list of exported
functions.

    :filter_reachable_from --keep-builtins exports

Remove <span style="display: inline-block;">`--keep-builtins`</span>
flag to remove unreachable prelude functions. Be aware the removal of
built-ins functions better to be used at the end of the pass pipeline to
avoid missing functions after optimization.

### 7.2. Eliminate <span style="display: inline-block;">`typedef`</span>

    :xform_named_type

### 7.3. Eliminate bit and int arithmetic operation

Eliminate bit,int arithmetic operations like "'000' + 3".

    :xform_desugar

### 7.4. Eliminate bit vector concatenate

Eliminate bit-tuples like "\[x,y\] = z;" and "x\[7:0, 15:8\]".

    :xform_bittuples

### 7.5. Convert bit-slice operation

Convert all bit-slice operations to use the +: syntax, e.g., "x\[7:0\]"
–&gt; "x\[0 +: 8\]".

    :xform_lower

### 7.6. Eliminate slices of integers

Eliminate slices of integers by first converting the integer to a
bitvector. E.g., if <span style="display: inline-block;">`x`</span> is
of <span style="display: inline-block;">`integer`</span> type, then
convert <span style="display: inline-block;">`x[1 +: 8]`</span> to
<span style="display: inline-block;">`cvt_int_bits(x, 9)[1 +: 8]`</span>

    :xform_int_bitslices

### 7.7. Convert getter/setter

Convert use of getter/setter syntax to function calls. E.g.,
<span style="display: inline-block;">`Mem[a, sz] = x`</span> –&gt;
<span style="display: inline-block;">`Mem_set(a, sz, x)`</span>

    :xform_getset

### 7.8. TODO

    :xform_valid track-valid

### 7.9. Constant propagation

Perform constant propagation without unrolling loops. This helps
identify potential targets for the monomorphicalize pass.

    :xform_constprop --nounroll

### 7.10. Monomorphicalize functions

Create specialized versions of every bitwidth-polymorphic function and
change all function calls to use the appropriate specialized version.
(Note that this performs an additional round of constant propagation.)

    :xform_monomorphize --auto-case-split

### 7.11. Lift let-expressions

Lift let-expressions as high as possible out of an expression e.g.,
<span style="display: inline-block;">`F(G(let t = 1 in H(t))) -> let t = 1 in F(G(H(t)))`</span>.

(This makes later transformations work better if, for example, they
should match against
<span style="display: inline-block;">`G(H(..))`</span>)

Note that source code is not expected to contain let-expressions. They
only exist to make some transformations easier to write.

    :xform_hoist_lets

### 7.12. Convert bit vector slice operation to bit operation

Convert bitslice operations like "x\[i\] = '1';" to a combination of
AND/OR and shift operations like "x = x OR (1 &lt;&lt; i);" This works
better after constant propagation/monomorphization.

    :xform_bitslices

Add flag <span style="display: inline-block;">`--notransform`</span>
when using <span style="display: inline-block;">`ac`</span>,
<span style="display: inline-block;">`sc`</span> backend.

### 7.13. Convert match to if

Any case statement that does not correspond to what the C language
supports is converted to an if statement. This works better after
constant propagation/monomorphization because that can
eliminate/simplify guards on the clauses of the case statement.

    :xform_case

### 7.14. Wrap variable

    :xform_wrap

### 7.15. Create bounded int

    :xform_bounded

### 7.16. Filter function not listed in imports

The <span style="display: inline-block;">`imports`</span> field should
be defined in configuration in following form:

    {
      "imports": [
        "TraceMemRead",
        "TraceMemWrite"
      ]
    }

    :filter_unlisted_functions imports

### 7.17. Check monomorphic

Check that all definitions are bitwidth-monomorphic and report a useful
error message if they are not. The code generator will produce an error
message if it finds a call to a polymorphic functions but we can produce
a much more useful error message if we scan for all polymorphic
functions and organize the list of functions into a call tree so that
you can see which functions are at the roots of the tree (and therefore
are the ones that you need to fix).

    :check_monomorphization --fatal --verbose
