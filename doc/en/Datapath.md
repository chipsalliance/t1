# Datapath of the vector coprocessor

## Introduction

The instructions are from the `V.request`, send `instruction`, `rs1Data`, `rs2Data` fields to the `V`:
  - decoder will decode the instruction on the wire, thus on the other(the scalar part) should provide a register to timing
  - These information will be latched to `V.requestReg`, which also contains the `V.instructionCounter`(tag) for this instruction for recording.
  - it also maintained a 1 depth queue for backpressure.

### Instruction Slots
The state of outstanding vector instructions are maintained in the `V.slots`, which is design as a component to record instruction state, including:
  - wLast
  - idle
  - sExecute
  - sCommit
