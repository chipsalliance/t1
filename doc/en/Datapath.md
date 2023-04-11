# Datapath of the vector coprocessor

## Introduction

The instructions are from the `V.request`, send `instruction`, `rs1Data`, `rs2Data` fields to the `V`:
  - decoder will decode the instruction on the wire, thus on the other(the scalar part) should provide a register to timing
  - These information will be latched to `V.requestReg`, which also contains the `V.instructionCounter`(tag) for this instruction for recording.
  - it also maintained a 1 depth queue for backpressure.

The next cycle will try to enqueue the instruction into slot.

### Instruction Slots
The state of outstanding vector instructions are maintained in the `V.slots`, which is designed as a component to record instruction state, including:
  - wLast
  - idle
  - sExecute
  - sCommit

Slot will store 3 fields for each instruction:
  - instructionIndex
  - hasCrossWrite
  - isLoadStore

The last slot is a special slot to maintain, it can enqueue a normal instruction, but these instructions can only enqueue to it:
  - mask unit
  - Lane <-> Top has data exchange(top might forward to LSU.)
  - unordered instruction(slide)

##