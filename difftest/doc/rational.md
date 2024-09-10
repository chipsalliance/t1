### Rational Documentation: Offline Difftest

#### Background
In existing online difftest solutions, step-by-step implementations often lack general applicability, especially when dealing with complex instruction streams and diverse processor architectures. Therefore, an **offline difftest system** is proposed, aimed at providing more detailed validation of each instruction’s execution result, along with an interface for simulators to handle **undefined behavior** injection.

#### Objective
The goal of offline difftest is to ensure the correctness of each instruction’s observed behavior during processor execution, while providing a flexible validation mechanism for handling undefined behavior. The validation process involves three steps:

1. Running the driver to generate the DUT (Design Under Test) trace.
2. Using the DUT trace to generate the model trace via reference model. e.g. Spike, SAIL, QEMU
3. Detecting instruction differences by comparing the DUT trace and model trace using the difftest algorithm.

#### Design Choices

##### 1. **DUT Trace Design and Encoding**
   - **Design:** The DUT trace design must consider how to efficiently and accurately record the execution state of each instruction, including the execution address, register values, memory state, etc. Currently, using `printf` to output the simulation address is a preliminary solution, with plans to possibly use [XDMA](https://docs.amd.com/r/en-US/pg347-cpm-dma-bridge/XDMA-Subsystem) to extract FPGA traces in the future for higher verification efficiency.
   - **Encoding:** Proper encoding of the trace will facilitate fast alignment and parsing in the comparison stage.

##### 2. **Simulator Integration**
   - **Using SAIL Simulator:** SAIL offers a formal verified simulation framework, which is also each to patch for the undefined implementations. 
     - **Removing Platform Dependencies:** To simplify the simulation, platform-related parts of SAIL will be removed, focusing on validating the instruction set and processor core.
     - **Function Patch:** For example, in validating FP (floating-point) related instructions, certain functions, such as `fp reduce order`, may need to be patched.
     - **Handling Undefined Behavior:** To verify undefined behavior, an interface will be provided for the simulator to inject specific behavior. This may involve adding external functions to SAIL and patching its source code.

##### 3. **DiffTest Algorithm**
   - **Instruction Alignment:** During the comparison process, it’s crucial to ensure that the DUT and model instruction streams are properly aligned. This may require special alignment algorithms to handle challenges arising from out-of-order commit.
   - **Comparison Algorithm:** The core of the comparison algorithm is to ensure that the observance of each instruction being same. Due to most of OoO write-back strategy, comparison commit result is the core reason of difftest framework diverse from different core projects.

#### Potential Challenges and Solutions

##### 1. **Memory Behavior Issues**
   - When memory access order differs, it may lead to processor behavior discrepancies. In such cases, alignment strategies and memory model simulation may be needed.

##### 2. **Handling Undefined Behavior**
   - Defining and simulating undefined behavior is a complex task. A flexible interface is needed to allow the simulator to inject undefined behavior when detected, and to record related traces for further analysis.
