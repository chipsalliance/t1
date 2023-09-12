# Introduction

The project focuses on developing a RISC-V Long Vector Machine Hardware
Generator using the Chisel language. It complies with RISC-V Vector Spec 1.0 and
supports specific extensions. The architecture can integrate with any RISC-V
scalar core using an Out-of-Order (OoO) write-back scheme. Key features include
a fully pipelined Vector Function Unit (VFU), configurable Load Store Unit (LSU),
and a Vector Register File (VRF) based on Dual-port SRAM. The design aims to
balance bandwidth, area, and frequency, offering configurable options for high
efficiency or performance. Microarchitecture tuning is guided by principles that
address bandwidth limitations and memory frequency. The methodology for
performance tuning involves determining VLEN, memory type, required bandwidth,
and vector cache size.
