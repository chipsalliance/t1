# Vector

This is a RISC-V Vector RTL generator, supporting Zvl1024b,Zve32x extension for now.
More documentation will be released after the first release.

## Test
We use [spike](https://github.com/riscv/riscv-isa-sim) for reference model.  
run with `mill -i tests.smoketest.run --cycles 100` for a single test.  
The simulator record events of vector register file and memory load store to perform online difftest.  
The simulator use spike to emulate the RISC-V core, which means this vector generator doesn't provide a hart implmentation.  

## Patches
<!-- BEGIN-PATCH -->
musl https://github.com/sequencer/musl/compare/master...riscv32.diff
chisel3 https://github.com/chipsalliance/chisel3/compare/master...circt_aop.diff
<!-- END-PATCH -->
