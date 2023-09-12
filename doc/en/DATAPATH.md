# Datapath of the vector coprocessor

## Introduction

The instructions are from the `V.request`, send `instruction`, `rs1Data`, `rs2Data` fields to the `V`:

- decoder will decode the instruction on the wire, thus on the other(the scalar part) should provide a register to timing
- These information will be latched to `V.requestReg`, which also contains the `V.instructionCounter`(tag) for this instruction for recording.
- it also maintained a 1 depth queue for backpressure.

The next cycle will try to enqueue the instruction into slot. For most of the instructions, they will be decoded and enqueued to each lane.

In side lane, there will be multiple slots instructions, the instruction enqueue to lane will be send to `entranceControl`,
which is a repackage to the `laneRequest` and `csr`, it will also initialize the state machine for the instruction,
the state machine is cycled for each group, thus we need to maintain the `initState` for the state machine.

The slots start to shift in these rules:

- instruction can only enqueue to the last slot.
- all slots can only shift at the same time which means:
  if one slot is finished earlier -> 1101, it will wait for the first slot to finish -> 1100, and then take two cycles to move to xx11.
  this will block the new instruction to enqueue, this might affect performance. But if not doing so, the circuit will be too complex.

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
- Lane <-> Top has data exchange(top might forward to LSU(indexed).)
- unordered instruction(slide)
  These instructions need exchange data from V and LSU.
  Additional channel `laneResponseFeedback` is required to feedback the response to Lane.

## Lane Control

The control of lane is maintained in `Lane.slotControl`,
this slot is used for record the state of each instruction send to the Lane(including indexed LSU and some VRF readonly mask instructions in lane).

## Cross Lane Channel

Lane has a ring(for now) bus for communication between lanes. It is used for data exchange between different lanes for operations that require cross-lane computation or data sharing.