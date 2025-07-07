## Contents

1.Introduction <span style="display: inline-block;"></span> ⁠1

1.1.How to build this document
<span style="display: inline-block;"></span> ⁠1

1.2.Resources <span style="display: inline-block;"></span> ⁠1

1.3.Compatibility <span style="display: inline-block;"></span> ⁠1

2.Overview <span style="display: inline-block;"></span> ⁠1

2.1.ASL Model <span style="display: inline-block;"></span> ⁠1

2.2.Rust Simulator <span style="display: inline-block;"></span> ⁠1

2.3.Differential Testing with Spike
<span style="display: inline-block;"></span> ⁠1

3.Exception <span style="display: inline-block;"></span> ⁠1

3.1.Exception API <span style="display: inline-block;"></span> ⁠1

3.2.Exception Causes <span style="display: inline-block;"></span> ⁠1

3.3.Trap Handling <span style="display: inline-block;"></span> ⁠1

4.Instruction Sets <span style="display: inline-block;"></span> ⁠1

4.1.Instruction file convention
<span style="display: inline-block;"></span> ⁠1

4.1.1.Example <span style="display: inline-block;"></span> ⁠1

4.2.Writing Instruction Semantics
<span style="display: inline-block;"></span> ⁠1

4.2.1.How It Works <span style="display: inline-block;"></span> ⁠1

4.2.2.Developers Responsibilities
<span style="display: inline-block;"></span> ⁠1

4.2.3.Example: implementing the
<span style="display: inline-block;">`addi`</span> instruction in
<span style="display: inline-block;">`rv_i`</span>
<span style="display: inline-block;"></span> ⁠1

4.3.Arg Luts <span style="display: inline-block;"></span> ⁠1

4.4.CSR <span style="display: inline-block;"></span> ⁠1

4.4.1.Reading and Writing CSRs
<span style="display: inline-block;"></span> ⁠1

4.4.2.Implementing CSR Handlers
<span style="display: inline-block;"></span> ⁠1

4.4.2.1.Example: Implementing
<span style="display: inline-block;">`misa`</span>
<span style="display: inline-block;"></span> ⁠1

5.Architecture States <span style="display: inline-block;"></span> ⁠1

5.1.Architecture State vs. CSR Implementation
<span style="display: inline-block;"></span> ⁠1

5.2.General Propose Register (GPRs)
<span style="display: inline-block;"></span> ⁠1

5.3.Control and Status Register (CSRs)
<span style="display: inline-block;"></span> ⁠1

5.3.1.Read only zeros CSRs <span style="display: inline-block;"></span>
⁠1

5.3.2.Machine ISA (misa) Register
<span style="display: inline-block;"></span> ⁠1

5.3.3.Machine status (mstatus) Register
<span style="display: inline-block;"></span> ⁠1

5.3.4.Machine Cause (mcause) Register
<span style="display: inline-block;"></span> ⁠1

5.3.5.Machine Interrupt (mip and mie) Registers
<span style="display: inline-block;"></span> ⁠1

5.3.6.Machine Trap Vector (mtvec) Register
<span style="display: inline-block;"></span> ⁠1

5.4.Machine Trap Value (mtval) Register
<span style="display: inline-block;"></span> ⁠1

5.5.Machine Exception Program Counter (mepc) Register
<span style="display: inline-block;"></span> ⁠1

6.Trap Handling <span style="display: inline-block;"></span> ⁠1

6.1.Exception <span style="display: inline-block;"></span> ⁠1

6.2.Interrupt <span style="display: inline-block;"></span> ⁠1

7.Rust Simulator <span style="display: inline-block;"></span> ⁠1

7.1.File Structures <span style="display: inline-block;"></span> ⁠1

7.2.Simulator States <span style="display: inline-block;"></span> ⁠1

7.3.FFI functions <span style="display: inline-block;"></span> ⁠1

7.3.1.Memory Read Write APIs
<span style="display: inline-block;"></span> ⁠1

7.4.Hook APIs <span style="display: inline-block;"></span> ⁠1

7.5.Trap APIs <span style="display: inline-block;"></span> ⁠1

7.6.Debug APIs <span style="display: inline-block;"></span> ⁠1

8.Build Instruction <span style="display: inline-block;"></span> ⁠1

8.1.Debug generated code <span style="display: inline-block;"></span> ⁠1

8.2.Get archived ASL library
<span style="display: inline-block;"></span> ⁠1

8.3.Compile final simulator <span style="display: inline-block;"></span>
⁠1

9.Appendix A: <span style="display: inline-block;">`ASLi`</span>
commands for lowering <span style="display: inline-block;"></span> ⁠1

9.1.Filter unreachable code from exports
<span style="display: inline-block;"></span> ⁠1

9.2.Eliminate <span style="display: inline-block;">`typedef`</span>
<span style="display: inline-block;"></span> ⁠1

9.3.Eliminate bit and int arithmetic operation
<span style="display: inline-block;"></span> ⁠1

9.4.Eliminate bit vector concatenate
<span style="display: inline-block;"></span> ⁠1

9.5.Convert bit-slice operation
<span style="display: inline-block;"></span> ⁠1

9.6.Eliminate slices of integers
<span style="display: inline-block;"></span> ⁠1

9.7.Convert getter/setter <span style="display: inline-block;"></span> ⁠1

9.8.TODO <span style="display: inline-block;"></span> ⁠1

9.9.Constant propagation <span style="display: inline-block;"></span> ⁠1

9.10.Monomorphicalize functions
<span style="display: inline-block;"></span> ⁠1

9.11.Lift let-expressions <span style="display: inline-block;"></span> ⁠1

9.12.Convert bit vector slice operation to bit operation
<span style="display: inline-block;"></span> ⁠1

9.13.Convert match to if <span style="display: inline-block;"></span> ⁠1

9.14.Wrap variable <span style="display: inline-block;"></span> ⁠1

9.15.Create bounded int <span style="display: inline-block;"></span> ⁠1

9.16.Filter function not listed in imports
<span style="display: inline-block;"></span> ⁠1

9.17.Check monomorphic <span style="display: inline-block;"></span> ⁠1

10.Appendix B: Coding convention
<span style="display: inline-block;"></span> ⁠1

10.1.Use Explicit Type Declarations
<span style="display: inline-block;"></span> ⁠1

10.2.Avoid Deeply Nested Operations
<span style="display: inline-block;"></span> ⁠1

10.3.Declare architecture states with UPPERCASE
<span style="display: inline-block;"></span> ⁠1

10.4.Pad Literal Bit Vectors for Clarity
<span style="display: inline-block;"></span> ⁠1

10.5.Declare local variable with lower\_case
<span style="display: inline-block;"></span> ⁠1

10.6.Use double underscore to indicate private value
<span style="display: inline-block;"></span> ⁠1

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

> **Why using Pokedex as project name**
>
> Our T1 testbench works with a collection of micro-architecture
> designs, each of which is named after a Pokémon. We chose the name
> Pokedex for this project to align with that theme.
>
> In the world of Pokémon, a Pokédex is an essential tool for
> understanding the creatures you interact with. In the same spirit,
> this project provides the tools to help us better understand,
> maintain, and improve our T1 architectures.

### 1.1. How to build this document

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

### 1.2. Resources

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

### 1.3. Compatibility

Note that we are using Intel Labs fork of ASL Interpreter, which doesn't
strictly implementing the ASL

## 2. Overview

This project provides a simulator for a custom RISC-V Instruction Set
Architecture (ISA) model. The simulator can load an ELF file, execute
its instructions, and log all architectural state changes, such as
register and CSR modifications.

Its primary goal is to serve as a "source of truth" for the ISA's
behavior, enabling co-simulation to identify bugs in Register Transfer
Level (RTL) designs.

Following is an overview of the project architecture:

The system is composed of two primary components: an **ASL Model** that
defines the ISA and a **Rust Simulator** that executes it.

### 2.1. ASL Model

We use ARM's Architecture Specification Language (ASL) to formally
describe the RISC-V ISA. The core logic is organized into three
categories within the
<span style="display: inline-block;">`model/`</span> directory:

-   **<span style="display: inline-block;">`csr/`</span>**: Contains
    code snippets for individual Control and Status Register (CSR)
    implementations.
-   **<span style="display: inline-block;">`extensions/`</span>**: Holds
    code snippets defining the semantics for each instruction, organized
    by ISA extension.
-   **<span style="display: inline-block;">`handwritten/`</span>**:
    Includes foundational, manually written code, such as architectural
    state declarations and helper libraries.

To improve accuracy and reduce manual effort, significant parts of the
model are code-generated based on the official
<span style="display: inline-block;">`riscv-opcodes`</span> repository.
This includes instruction decoders, dispatch logic, and CSR read/write
dispatchers.

### 2.2. Rust Simulator

The ASL model defines **what** the ISA does but not **how** to run it.
The simulation environment is provided by a platform written in Rust. It
links to the compiled ASL model and handles all runtime responsibilities
that the model does not, including:

-   Command-line argument parsing
-   Memory allocation and maintenance
-   Logging utilities
-   Interrupt handling
-   Driving the simulation loop

The simulator's entry point is
<span style="display: inline-block;">`simulator/pokedex/src/bin/pokedex.rs`</span>,
which parses command-line arguments and runs the main simulation loop.

The simulator logic is organized into several key modules:

-   **<span style="display: inline-block;">`simulator.rs`</span>**: The
    core simulation driver, responsible for managing memory and
    interrupts.
-   **<span style="display: inline-block;">`ffi.rs`</span>**: Exposes
    Rust functions (like memory access) to the ASL model through a
    C-compatible API.
-   **<span style="display: inline-block;">`model.rs`</span>**: A
    wrapper around the C code that is auto-generated from the ASL model
    by <span style="display: inline-block;">`bindgen`</span>.

The ASL model communicates with the Rust simulator through a Foreign
Function Interface (FFI). The ASL code is first compiled into a C
archive (<span style="display: inline-block;">`libpokedex_sim.a`</span>)
with corresponding header files. The Rust simulator then uses these
C-bindings to step each instruction and handle I/O operations like
memory loads and stores.

The <span style="display: inline-block;">`build.rs`</span> script
orchestrates the build process. It read the ASLi generated C code and
then uses the <span style="display: inline-block;">`bindgen`</span> tool
to generate Rust bindings from the
<span style="display: inline-block;">`asl_export.h`</span> header file.
These bindings are then used by the
<span style="display: inline-block;">`model.rs`</span> module to
interact with the ISA model.

Implementation details can be found at chapter Section 7.

### 2.3. Differential Testing with Spike

To ensure our model's correctness, we perform differential testing
against <span style="display: inline-block;">`riscv-isa-sim`</span>
(Spike), the official RISC-V golden model. By comparing our model's
architectural state changes against Spike's on a per-instruction basis,
we can verify that our implementation is trustworthy and accurate.

The <span style="display: inline-block;">`difftest`</span> CLI will read
a configuration where user specify the directory for all test cases and
arguments for simulator and spike. It will run simulator and spike
automatically, then get corresponding commit log and parse to structure
metadata for comparing. When any part of the log is mismatched, like
missing register read/write operation or register get written with
different value at same point, the
<span style="display: inline-block;">`difftest`</span> CLI will run fail
and provide dump near the error place.

> By default, differential testing for memory operations is disabled.
>
> This is because the behavior of the memory system is platform-specific
> and not defined by the ISA specification, making a direct comparison
> between different simulators impractical for these operations.

## 3. Exception

### 3.1. Exception API

To handle operations that may fail, our ASL model emulates Rust's
<span style="display: inline-block;">`Result`</span> type. Since ASL
does not support generic enums, we use a custom
<span style="display: inline-block;">`record`</span> and a set of helper
functions to provide a standardized way of returning either a successful
value or an exception.

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

-   **<span style="display: inline-block;">`OK(value)`</span>**: Use
    this to return a successful result. It creates a
    <span style="display: inline-block;">`Result`</span> with
    <span style="display: inline-block;">`is_ok`</span> set to
    <span style="display: inline-block;">`TRUE`</span> and the
    <span style="display: inline-block;">`value`</span> field populated.
-   **<span style="display: inline-block;">`Exception(cause, trap_value)`</span>**:
    Use this to return a failure. It creates a
    <span style="display: inline-block;">`Result`</span> with
    <span style="display: inline-block;">`is_ok`</span> set to
    <span style="display: inline-block;">`FALSE`</span>, the
    <span style="display: inline-block;">`cause`</span> field set to the
    exception type, and the
    <span style="display: inline-block;">`trap_value`</span> field
    holding relevant context about the error (e.g., the faulting
    address).
-   **<span style="display: inline-block;">`Retired()`</span>**: Use
    this for successful operations that do not produce a return value.
    It creates a <span style="display: inline-block;">`Result`</span>
    with <span style="display: inline-block;">`is_ok`</span> set to
    <span style="display: inline-block;">`TRUE`</span>,
    <span style="display: inline-block;">`cause`</span> set to
    <span style="display: inline-block;">`-1`</span> and
    <span style="display: inline-block;">`value`</span> set to zeros.

### 3.2. Exception Causes

While the <span style="display: inline-block;">`Exception`</span>
function can be called with any custom
<span style="display: inline-block;">`cause`</span> ID, most exceptions
should use the standard cause codes defined by the RISC-V privilege
specification.

We auto-generate these cause codes as named constants from the
[<span style="display: inline-block;">`causes.csv`</span>](https://github.com/riscv/riscv-opcodes/blob/master/causes.csv)
file in the official
<span style="display: inline-block;">`riscv-opcodes`</span> repository.
For each entry in the CSV, a constant is generated with the
<span style="display: inline-block;">`CAUSE_`</span> prefix, followed by
the uppercase description with spaces replaced by underscores.

For example, the description "Misaligned load" becomes the constant
<span style="display: inline-block;">`CAUSE_MISALIGNED_LOAD`</span>.

**Example Usage:**

To return a misaligned load exception, you would use the generated
constant like this:

    // ...
    // Check if the address is misaligned.
    if addr[1:0] != '00' then
      // Return an exception with the standard cause and the faulting address.
      return Exception(CAUSE_MISALIGNED_LOAD, addr);
    end

    // ...

### 3.3. Trap Handling

Trap handling is explained at Section 6.

## 4. Instruction Sets

This model implements the RISC-V instruction set architecture based on
the official
<span style="display: inline-block;">`riscv-isa-manual`</span>
repository. Our implementation specifically adheres to the latest
ratified version released on May 08, 2025.

For reference, developers can download the official specification
document for from the following link:

-   **RISC-V ISA Specification (Release 2025-05-08)**:
    <https://github.com/riscv/riscv-isa-manual/releases/tag/20250508>

This section contains details guidance of how we describe instruction
semantics.

### 4.1. Instruction file convention

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

#### 4.1.1. Example

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

### 4.2. Writing Instruction Semantics

The logic for each instruction is written in its own
<span style="display: inline-block;">`.asl`</span> file (e.g.,
<span style="display: inline-block;">`addi.asl`</span>). This file
contains **only the body** of the instruction's execution logic. The
<span style="display: inline-block;">`codegen`</span> CLI tool
automatically wraps this logic in a full function signature and adds a
call to it from a global dispatcher. Developer **should not** write the
function signature yourself.

#### 4.2.1. How It Works

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

    func Execute_<INSTRUCTION_NAME>(instruction: bits(32)) => Result

**2. Creates a Dispatch Case**: It adds a pattern match case to the
global <span style="display: inline-block;">`Execute()`</span> function.
This dispatcher inspects the opcode of every incoming instruction and
calls the corresponding
<span style="display: inline-block;">`Execute_<INSTRUCTION_NAME>`</span>
function.

#### 4.2.2. Developers Responsibilities

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
-   **Return Result**: Developer should return
    <span style="display: inline-block;">`Result`</span> value after
    handling the execution.

#### 4.2.3. Example: implementing the <span style="display: inline-block;">`addi`</span> instruction in <span style="display: inline-block;">`rv_i`</span>

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

**Step 3: Review the Generated Code**

After running the <span style="display: inline-block;">`codegen`</span>
tool, the <span style="display: inline-block;">`addi.asl`</span> snippet
will be integrated into the model. The final generated code will look
like this:

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

### 4.3. Arg Luts

To reduce decoding bugs and simplify development, extracting arg field
from instruction is abstracted to functions implemented in
<span style="display: inline-block;">`handwritten/arg.asl`</span> file.

The arg APIs follow a consistent naming and signature convention:

-   Naming: All functions start with a
    <span style="display: inline-block;">`Get`</span> prefix, followed
    by the field name in uppercase (e.g.,
    <span style="display: inline-block;">`GetRD`</span>,
    <span style="display: inline-block;">`GetRS1`</span>).
-   Signature: Each function accepts the 32-bit instruction as a
    <span style="display: inline-block;">`bits(32)`</span> parameter and
    returns a bit vector whose size is determined by the field's
    definition in the lookup table.

For example, instead of manually slicing the immediate field for each
J-type instruction, developer should use the provided
<span style="display: inline-block;">`GetJIMM`</span> API. This function
takes the 32-bits instruction and returns the corresponding 20-bit
immediate value.

    func GetJIMM(inst : bits(32)) => bits(20)
    begin
      let imm20 : bit = inst[31];
      let imm10_1 : bits(10) = inst[30:21];
      let imm11 : bit = inst[20];
      let imm19_12 : bits(8) = inst[19:12];

      return [imm20, imm19_12, imm11, imm10_1];
    end

### 4.4. CSR

This section contains basic information of how to implement Control and
Status Register (CSR) to models.

#### 4.4.1. Reading and Writing CSRs

All CSRs operations are handled by two main APIs that are generated by
the <span style="display: inline-block;">`codegen`</span> CLI tool.

-   Read API:
    <span style="display: inline-block;">`func ReadCSR(csr_number: bits(12)) => Result`</span>
-   Write API:
    <span style="display: inline-block;">`func WriteCSR(csr_number: bits(12), value: bits(32)) => Result`</span>

The generated code works as a dispatcher. It uses pattern matching on
the <span style="display: inline-block;">`csr_number`</span> parameter
to identify the target CSR and then calls the corresponding handler
function that you have implemented for that specific register.

For example, the generated code will look similar to this:

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

#### 4.4.2. Implementing CSR Handlers

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
<span style="display: inline-block;">`Result`</span> type.

**Write Handlers:** The tool generates a write function named
<span style="display: inline-block;">`Write_<CSR_NAME>`</span>. Your
<span style="display: inline-block;">`.asl`</span> file in the
<span style="display: inline-block;">`csr/write/`</span> directory must
contain the logic to handle the write operation. The new value is passed
in as the <span style="display: inline-block;">`value`</span> argument
with <span style="display: inline-block;">`XLEN`</span> bits. For
read-only CSR, a write handler must return illegal instruction
exception.

##### 4.4.2.1. Example: Implementing <span style="display: inline-block;">`misa`</span>

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

**3. Implement the Write Logic
(<span style="display: inline-block;">`write/misa_301.asl`</span>)**

Add the corresponding logic to
<span style="display: inline-block;">`csr/write/misa_301.asl`</span>.
This becomes the body of the
<span style="display: inline-block;">`Write_MISA(value: bits(32))`</span>
function.

    return Retired();

**4. Final Generated Code**

After running <span style="display: inline-block;">`codegen`</span> CLI,
the tool will take your
<span style="display: inline-block;">`.asl`</span> snippets and produce
the following complete, callable functions:

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

## 5. Architecture States

All architectural states for current ISA model, from general-purpose
registers to Control and Status Registers, are defined in the
<span style="display: inline-block;">`states.asl`</span> file. To
optimize the model, we only define the specific bits necessary for the
supported ISA features.

This section serves as a reference for all architectural states
maintained by the model.

### 5.1. Architecture State vs. CSR Implementation

The relationship between an **Architectural State** and a **Control and
Status Register (CSR)** is that of implementation versus interface.

Developer can think of **Architectural States** as the actual, physical
variables in our model. These are custom-sized registers that hold the
state of the model.

In contrast, **CSRs** are the standardized, abstract interface used to
access those underlying architectural states. Because of this
separation, we only implement the architectural state bits that are
necessary for our model's features, rather than defining storage for
every bit of every CSR in the specification.

The RISC-V specification defines several types of CSRs, such as **WPRI**
(Write-Preserve, Read-Ignore), **WLRL** (Write-Legal, Read-Legal), and
**WARL** (Write-Any, Read-Legal). These types dictate how writes are
handled—for instance, some fields in a write may be consider invalid and
be ignored.

To ensure our model correctly adheres to these rules, we do not expose
the raw architectural states directly. Instead, we provide constrained
getter and setter APIs for each state. This design imposes the
specification's rules at the access layer, guaranteeing that any
register write performed by your instruction logic is automatically
handled correctly according to the CSR's type, and consider any invalid
write as "model implementation bugs".

As a **Write-Any, Read-Legal (WARL)** register, the
<span style="display: inline-block;">`mtvec`</span> CSR provides a clear
example of separating the public-facing CSR from its underlying
architectural states.

The <span style="display: inline-block;">`mtvec`</span> CSR is composed
of two architectural states:

-   <span style="display: inline-block;">`BASE`</span>: A 30-bit field
    for the trap address.
-   <span style="display: inline-block;">`MODE`</span>: A 2-bit field
    for the trap mode.

The key distinction lies in how writes are handled:

1.  **CSR Write Handler (Permissive):** As a WARL register, any 32-bit
    value can be written to the
    <span style="display: inline-block;">`mtvec`</span> CSR. The CSR's
    write logic is responsible for sanitizing this input. For example,
    the <span style="display: inline-block;">`MODE`</span> field only
    supports values <span style="display: inline-block;">`0b00`</span>
    and <span style="display: inline-block;">`0b01`</span>. If a write
    contains <span style="display: inline-block;">`0b10`</span> or
    <span style="display: inline-block;">`0b11`</span> in the mode bits,
    the handler correctly ignores the update for that field, treating it
    as a no-op.

2.  **Architectural State (Strict):** The internal
    <span style="display: inline-block;">`MODE`</span> state itself
    should **never** contain an illegal value like
    <span style="display: inline-block;">`0b10`</span> or
    <span style="display: inline-block;">`0b11`</span>. The sanitization
    logic in the CSR handler guarantees this. Therefore, our model's
    internal logic will use an **assertion** to validate the
    <span style="display: inline-block;">`MODE`</span> state. If this
    assertion ever fails, it signals a critical bug in the CSR's
    write-handling or some explicit write logic that must be fixed.

### 5.2. General Propose Register (GPRs)

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
flag is provided. Developer should also ensure
<span style="display: inline-block;">`--runtime-check`</span> flag is
provided to avoid invalid type cast.

### 5.3. Control and Status Register (CSRs)

This sections contains CSRs behavior in current model. If a CSR address
not contains in this section get read or write, the dispatcher will
raise illegal instruction exception.

#### 5.3.1. Read only zeros CSRs

Registers covered in this section are always read-only zero. Any write
to these registers will return a illegal instruction exception. Also no
architecture states will be allocated for these CSRs.

-   <span style="display: inline-block;">`mvendorid`</span>
-   <span style="display: inline-block;">`marchid`</span>
-   <span style="display: inline-block;">`mimpid`</span>
-   <span style="display: inline-block;">`mhartid`</span>
-   <span style="display: inline-block;">`mconfigptr`</span>

#### 5.3.2. Machine ISA (misa) Register

Switching extension supports at runtime is not supported by this model.
The <span style="display: inline-block;">`misa`</span> register is
implemented as a read-only CSR register. A read to
<span style="display: inline-block;">`misa`</span> register always
return a static bit vector indicating current enabled extensions. Any
writes to <span style="display: inline-block;">`misa`</span> register
will return illegal instruction exception.

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

Our implementation will now return support for
<span style="display: inline-block;">`rv32imafc`</span>, 'x' is not
enabled for now.

Since <span style="display: inline-block;">`misa`</span> is a read-only
value, no states will be allocated in current model.

#### 5.3.3. Machine status (mstatus) Register

Current model focus on machine mode only, supervisor and user mode are
not implemented. So for
<span style="display: inline-block;">`mstatus`</span> register, model
only provide following fields for
<span style="display: inline-block;">`mstatus`</span> register:

-   <span style="display: inline-block;">`mie`</span>: global
    interrupt-enable bit for machine mode
-   <span style="display: inline-block;">`mpie`</span>: the
    interrupt-enable bit active prior to the trap
-   <span style="display: inline-block;">`mpp[1:0]`</span>: previous
    privilege level

The <span style="display: inline-block;">`mstatush`</span> register is
also not required since we only implement M-mode. Any write to
<span style="display: inline-block;">`mstatush`</span> is a no-op, and
read will get zeros.

The above bit fields will be declare as individual register in
<span style="display: inline-block;">`states.asl`</span> file.

    var MSTATUS_MIE : bit;
    var MSTATUS_MPIE : bit;
    var MSTATUS_MPP : PRIVILEGE_LEVEL;

Variables <span style="display: inline-block;">`MSTATUS_MIE`</span> and
<span style="display: inline-block;">`MSTATUS_MPIE`</span> are of one
bit type, and there are by default containing valid value, so no
constraints added to them.

Variable <span style="display: inline-block;">`MSTATUS_MPP`</span> only
holds one valid value (M mode) but should it have two bits, so a new
enumeration type
<span style="display: inline-block;">`PRIVILEGE_LEVEL`</span> is added
to limit the value.

    enumeration PRIVILEGE_LEVEL {
      PRIV_MACHINE_MODE
    };

Field <span style="display: inline-block;">`PRIV_MACHINE_MODE`</span>
will be converted to bits vector value
<span style="display: inline-block;">`0b11`</span>, and convert from
bits vector <span style="display: inline-block;">`0b11`</span>. Any
other value convert into type
<span style="display: inline-block;">`PRIVILEGE_LEVEL`</span> will be
seen as internal model bug:

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

An example read/write operation to
<span style="display: inline-block;">`mstatus`</span> register looks
like following:

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

#### 5.3.4. Machine Cause (mcause) Register

The <span style="display: inline-block;">`mcause`</span> register is
store in two states: a
<span style="display: inline-block;">`Interrupt`</span> bit register and
a 31 bits length exception code register:

    var MCAUSE_IS_INTERRUPT : boolean;
    var MCAUSE_XCPT_CODE : bits(31);

A read to the <span style="display: inline-block;">`mcause`</span> CSR
register will have a concatenated 32-bit value from above register, with
the interrupt bit at top, exception code value at bottom.

    function Read_MCAUSE()
    begin
      return [MCAUSE_IS_INTERRUPT_BIT, MCAUSE_EXCEPTION_CODE];
    end

Since <span style="display: inline-block;">`mcause`</span> CSR is a WLRL
register, we don't validate the value, it is up to software to verify
the correctness of CSR value.

    function Write_MCAUSE(value : bits(32))
    begin
      MCAUSE_IS_INTERRUPT_BIT = value[31];
      MCAUSE_XCPT_CODE = value[30:0];

      return Retired();
    end

#### 5.3.5. Machine Interrupt (mip and mie) Registers

We have only M-mode support in current implementation, so LCOFIP,
supervisor interrupt bits and software interrupt bit (MSIP/MSIE) are not
allocated and are read-only zero.

External interrupt pending bit (MEIP) and Timer interrupt pending bit
(MTIP) is controlled by external controller at semantic. We use FFI
functions
<span style="display: inline-block;">`FFI_machine_external_interrupt_pending`</span>
and
<span style="display: inline-block;">`FFI_machine_time_interrupt_pending`</span>
to get the value, and no states will be allocated for these two bits.
Any CSR write to <span style="display: inline-block;">`mip`</span> will
raise illegal instruction exception.

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

External interrupt enable (MEIE) and timer interrupt enable (MTIE) are
single bit register. They contains only two value and thus no
constraints will be imposed on these bits.

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

#### 5.3.6. Machine Trap Vector (mtvec) Register

Register <span style="display: inline-block;">`mtvec`</span> holds
address to the trap handler. It is implemented with two states:

-   <span style="display: inline-block;">`MTVEC_MODE`</span>:
    enumeration that contains only "direct" and "vectored" mode;
-   <span style="display: inline-block;">`MTVEC_BASE`</span>: 28 bits
    size bit vector holds the base address;

In current implementation,
<span style="display: inline-block;">`MTVEC_MODE`</span> only store two
mode. A write with
<span style="display: inline-block;">`value[1:0]`</span> larger or
equals to 2 is considered as implementation bug.

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

In definition, <span style="display: inline-block;">`MTVEC_BASE`</span>
inherently valid. Thus there is no constraint at architecture states
<span style="display: inline-block;">`MTVEC_BASE`</span>.

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

### 5.4. Machine Trap Value (mtval) Register

CSR <span style="display: inline-block;">`mtval`</span> will have full
32-bits state register
<span style="display: inline-block;">`MTVAL`</span> to store value. Read
and write this architecture states have no constraints.

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

### 5.5. Machine Exception Program Counter (mepc) Register

CSR <span style="display: inline-block;">`mepc`</span> have full 32-bits
states register <span style="display: inline-block;">`MEPC`</span> to
store value. Any write to the
<span style="display: inline-block;">`MEPC`</span> states must be
32-bits align. (We don't have C extension support now).

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

Thus developer should verify address before writing to
<span style="display: inline-block;">`mepc`</span>.

    func Write_MEPC(value : bits(32)) => Result
    begin
      // todo: C ext
      MEPC = [ value[31:2], '00' ];

      return Retired();
    end

## 6. Trap Handling

> We use the term exception to refer to an unusual condition occurring
> at run time associated with an instruction in the current RISC-V hart.
> We use the term interrupt to refer to an external asynchronous event
> that may cause a RISC-V hart to experience an unexpected transfer of
> control. We use the term trap to refer to the transfer of control to a
> trap handler caused by either an exception or an interrupt.

### 6.1. Exception

Each execution may return a failed
<span style="display: inline-block;">`Result`</span>, but the result
should continuously transfer to upper level, and finally get trapped at
<span style="display: inline-block;">`Step`</span> function. A trap
exception will update corresponding
<span style="display: inline-block;">`mstatus`</span>,
<span style="display: inline-block;">`mip`</span>… CSR, and set
<span style="display: inline-block;">`PC`</span> to the address stored
in <span style="display: inline-block;">`mtvec`</span> CSR.

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

### 6.2. Interrupt

Interrupt is check before starting to decode an instruction. A
<span style="display: inline-block;">`CheckInterrupt`</span> function
will read corresponding pending and enable bit to determine if model
should trap into the interrupt handler or not. If there is an interrupt,
the <span style="display: inline-block;">`Step`</span> function will
directly return instead of continuing decode and execute.

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

Handling trap for interrupt has similar logic as handling trap for
exception, but interrupt specific information will be written into CSR,
and PC is handled by least significant bits of
<span style="display: inline-block;">`mtvec`</span>.

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

## 7. Rust Simulator

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

### 7.1. File Structures

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

### 7.2. Simulator States

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

### 7.3. FFI functions

Following are FFI functions required from ASL model that developers
should implemented on Rust side. These function are explicitly declare
at <span style="display: inline-block;">`external.asl`</span> file.

> Due to monomorphic optimization, generated C functions will be suffix
> with unique ID and data size. Developers need to make sure that a
> correct function symbol is declared. They can check final function
> signatures at <span style="display: inline-block;">`*_types.h`</span>
> header file after ASLi generated C files.

#### 7.3.1. Memory Read Write APIs

All the memory API have following return type:

    record FFI_ReadResult(N) {
      success : boolean;
      data    : bits(N);
    };

After monomorphic transformation, this record type will became following
C <span style="display: inline-block;">`struct`</span>:

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

For memory read, model required following platform implementation:

    func FFI_instruction_fetch(pc : bits(32)) => FFI_ReadResult(32);

    func FFI_read_physical_memory_8bits(addr : bits(32)) => FFI_ReadResult(8);
    func FFI_read_physical_memory_16bits(addr : bits(32)) => FFI_ReadResult(16);
    func FFI_read_physical_memory_32bits(addr : bits(32)) => FFI_ReadResult(32);

Which will be translated into following C code:

    FFI_ReadResult_N_32 FFI_instruction_fetch_0(uint32_t pc);

    FFI_ReadResult_N_8 FFI_read_physical_memory_8bits_0(uint32_t addr);
    FFI_ReadResult_N_16 FFI_read_physical_memory_16bits_0(uint32_t addr);
    FFI_ReadResult_N_32 FFI_read_physical_memory_32bits_0(uint32_t addr);

For memory write, model required following platform implementation:

    func FFI_write_physical_memory_8bits(addr : bits(32), data : bits(8)) => boolean;
    func FFI_write_physical_memory_16bits(addr : bits(32), data : bits(16)) => boolean;
    func FFI_write_physical_memory_32bits(addr : bits(32), data : bits(32)) => boolean;

Platform should return
<span style="display: inline-block;">`false`</span> when a write violate
platform memory to indicate an access fault.

In current platform implementation, there is no memory protection, and
any memory violation will be capture and raise an memory load store
fault exception back to model. Unaligned read/write is not supported at
memory platform side and is also thrown as exception.

### 7.4. Hook APIs

Model required following hooks from platform to notify real time info of
model current behavior.

    // Execute when executing fence instruction
    func FFI_emulator_do_fence();
    // Execute when GPR get written
    func FFI_write_GPR_hook(reg_idx: integer{0..31}, data: bits(32));

### 7.5. Trap APIs

Model required platform provide following functions for
exception/interrupt trap supports.

    func FFI_machine_external_interrupt_pending() => bit;
    func FFI_machine_time_interrupt_pending() => bit;
    func FFI_ebreak();
    func FFI_ecall();

### 7.6. Debug APIs

Model required platform provide following functions implementation to
debug model.

    func FFI_print_str(s: string);
    func FFI_print_bits_hex(v: bits(32));

## 8. Build Instruction

### 8.1. Debug generated code

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

### 8.2. Get archived ASL library

Developers can get the archive file with following commands

    $ cd pokedex/model
    $ nix develop ".#pokedex.sim-lib" -c make install
    $ ls ./build/simlib
    include/  lib/

Generated headers are placed under the
<span style="display: inline-block;">`include/`</span> directory, and
the ASL model code is packaged under
<span style="display: inline-block;">`lib/`</span> directory.

### 8.3. Compile final simulator

Developer can get binary with following command:

    $ nix build '.#pokedex.simulator'
    $ ls ./result/bin

Or build the emulator by invoking cargo manually:

    $ nix develop '.#pokedex.simulator.dev'
    $ cd pokedex/simulator
    $ cargo build

## 9. Appendix A: <span style="display: inline-block;">`ASLi`</span> commands for lowering

### 9.1. Filter unreachable code from exports

This command discard any code not reachable from the list of exported
functions.

    :filter_reachable_from --keep-builtins exports

Remove <span style="display: inline-block;">`--keep-builtins`</span>
flag to remove unreachable prelude functions. Be aware the removal of
built-ins functions better to be used at the end of the pass pipeline to
avoid missing functions after optimization.

### 9.2. Eliminate <span style="display: inline-block;">`typedef`</span>

    :xform_named_type

### 9.3. Eliminate bit and int arithmetic operation

Eliminate bit,int arithmetic operations like "'000' + 3".

    :xform_desugar

### 9.4. Eliminate bit vector concatenate

Eliminate bit-tuples like "\[x,y\] = z;" and "x\[7:0, 15:8\]".

    :xform_bittuples

### 9.5. Convert bit-slice operation

Convert all bit-slice operations to use the +: syntax, e.g., "x\[7:0\]"
–&gt; "x\[0 +: 8\]".

    :xform_lower

### 9.6. Eliminate slices of integers

Eliminate slices of integers by first converting the integer to a
bitvector. E.g., if <span style="display: inline-block;">`x`</span> is
of <span style="display: inline-block;">`integer`</span> type, then
convert <span style="display: inline-block;">`x[1 +: 8]`</span> to
<span style="display: inline-block;">`cvt_int_bits(x, 9)[1 +: 8]`</span>

    :xform_int_bitslices

### 9.7. Convert getter/setter

Convert use of getter/setter syntax to function calls. E.g.,
<span style="display: inline-block;">`Mem[a, sz] = x`</span> –&gt;
<span style="display: inline-block;">`Mem_set(a, sz, x)`</span>

    :xform_getset

### 9.8. TODO

    :xform_valid track-valid

### 9.9. Constant propagation

Perform constant propagation without unrolling loops. This helps
identify potential targets for the monomorphicalize pass.

    :xform_constprop --nounroll

### 9.10. Monomorphicalize functions

Create specialized versions of every bitwidth-polymorphic function and
change all function calls to use the appropriate specialized version.
(Note that this performs an additional round of constant propagation.)

    :xform_monomorphize --auto-case-split

### 9.11. Lift let-expressions

Lift let-expressions as high as possible out of an expression e.g.,
<span style="display: inline-block;">`F(G(let t = 1 in H(t))) -> let t = 1 in F(G(H(t)))`</span>.

(This makes later transformations work better if, for example, they
should match against
<span style="display: inline-block;">`G(H(..))`</span>)

Note that source code is not expected to contain let-expressions. They
only exist to make some transformations easier to write.

    :xform_hoist_lets

### 9.12. Convert bit vector slice operation to bit operation

Convert bitslice operations like "x\[i\] = '1';" to a combination of
AND/OR and shift operations like "x = x OR (1 &lt;&lt; i);" This works
better after constant propagation/monomorphization.

    :xform_bitslices

Add flag <span style="display: inline-block;">`--notransform`</span>
when using <span style="display: inline-block;">`ac`</span>,
<span style="display: inline-block;">`sc`</span> backend.

### 9.13. Convert match to if

Any case statement that does not correspond to what the C language
supports is converted to an if statement. This works better after
constant propagation/monomorphization because that can
eliminate/simplify guards on the clauses of the case statement.

    :xform_case

### 9.14. Wrap variable

    :xform_wrap

### 9.15. Create bounded int

    :xform_bounded

### 9.16. Filter function not listed in imports

The <span style="display: inline-block;">`imports`</span> field should
be defined in configuration in following form:

    {
      "imports": [
        "TraceMemRead",
        "TraceMemWrite"
      ]
    }

    :filter_unlisted_functions imports

### 9.17. Check monomorphic

Check that all definitions are bitwidth-monomorphic and report a useful
error message if they are not. The code generator will produce an error
message if it finds a call to a polymorphic functions but we can produce
a much more useful error message if we scan for all polymorphic
functions and organize the list of functions into a call tree so that
you can see which functions are at the roots of the tree (and therefore
are the ones that you need to fix).

    :check_monomorphization --fatal --verbose

## 10. Appendix B: Coding convention

To ensure consistency and readability across the project, please adhere
to the following conventions when writing ASL code.

### 10.1. Use Explicit Type Declarations

Always provide a type annotation when declaring a variable with
<span style="display: inline-block;">`let`</span> or
<span style="display: inline-block;">`var`</span>. This practice
improves code clarity and helps prevent type-related errors.

Recommended:

    let i : integer = 0x1;

Avoid:

    let i = 0x1;

### 10.2. Avoid Deeply Nested Operations

Instead of nesting multiple function calls or operations in a single
statement, use intermediate variables to store the results of each step.
This makes the logic easier to read, understand, and debug.

Recommended:

    let a_ext : bits(32) = ZeroExtend(GPR[1]);
    let not_a : bits(32) = NOT(a_ext);

Avoid:

    let not_a : bits(32) = NOT(ZeroExtend(GPR[1]));

### 10.3. Declare architecture states with UPPERCASE

Architecture states reads like global variable in normal code, thus we
prefer using UPPERCASE for the states variable name.

Recommended:

    var MPP : bits(2)

Avoid:

    var mpp : bits(2)

### 10.4. Pad Literal Bit Vectors for Clarity

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

### 10.5. Declare local variable with lower\_case

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

### 10.6. Use double underscore to indicate private value

Function, value binding that should only be used in current file should
be prefixed with double underscore.

Recommended:

    val __MyPrivateValue : bits(32);

    func __HandleMyPrivateValue()
    begin
    end
